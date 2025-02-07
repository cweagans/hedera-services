/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.mono.txns.submission;

import static com.hedera.node.app.service.mono.txns.validation.PureValidation.queryableAccountStatus;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.mono.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.FeeExemptions;
import com.hedera.node.app.service.mono.legacy.exception.InvalidAccountIDException;
import com.hedera.node.app.service.mono.legacy.exception.KeyPrefixMismatchException;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.sigs.verification.PrecheckVerifier;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Determines if the payer account set in the {@code TransactionID} is expected to be both willing
 * and able to pay the transaction fees.
 *
 * <p>For more details, please see
 * https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md
 */
@Singleton
public class SolvencyPrecheck {
    private static final Logger log = LogManager.getLogger(SolvencyPrecheck.class);

    private static final TxnValidityAndFeeReq VERIFIED_EXEMPT = new TxnValidityAndFeeReq(OK);
    private static final TxnValidityAndFeeReq LOST_PAYER_EXPIRATION_RACE = new TxnValidityAndFeeReq(FAIL_FEE);

    private final FeeExemptions feeExemptions;
    private final FeeCalculator feeCalculator;
    private final OptionValidator validator;
    private final PrecheckVerifier precheckVerifier;
    private final Supplier<StateView> stateView;
    private final Supplier<AccountStorageAdapter> accounts;

    @Inject
    public SolvencyPrecheck(
            FeeExemptions feeExemptions,
            FeeCalculator feeCalculator,
            OptionValidator validator,
            PrecheckVerifier precheckVerifier,
            Supplier<StateView> stateView,
            Supplier<AccountStorageAdapter> accounts) {
        this.accounts = accounts;
        this.validator = validator;
        this.stateView = stateView;
        this.feeExemptions = feeExemptions;
        this.feeCalculator = feeCalculator;
        this.precheckVerifier = precheckVerifier;
    }

    TxnValidityAndFeeReq assessSansSvcFees(SignedTxnAccessor accessor) {
        return assess(accessor, false);
    }

    TxnValidityAndFeeReq assessWithSvcFees(SignedTxnAccessor accessor) {
        return assess(accessor, true);
    }

    private TxnValidityAndFeeReq assess(SignedTxnAccessor accessor, boolean includeSvcFee) {
        final var payerStatus = payerAccountStatus(EntityNum.fromAccountId(accessor.getPayer()));
        if (payerStatus != OK) {
            return new TxnValidityAndFeeReq(PAYER_ACCOUNT_NOT_FOUND);
        }

        final var sigsStatus = checkSigs(accessor);
        if (sigsStatus != OK) {
            return new TxnValidityAndFeeReq(sigsStatus);
        }

        if (hasExemptPayer(accessor)) {
            return VERIFIED_EXEMPT;
        }

        return solvencyOfVerifiedPayer(accessor, includeSvcFee);
    }

    /**
     * Checks if the payer account is exempt from paying fees. Public for now to
     * support a thin adapter to use in {@code IngestChecker}.
     *
     * TODO - replace all uses of this method with refactored ingest APIs
     *
     * @param accessor the accessor for the transaction
     * @return whether the payer account is exempt from paying fees
     */
    public boolean hasExemptPayer(final SignedTxnAccessor accessor) {
        return feeExemptions.hasExemptPayer(accessor);
    }

    /**
     * Checks if the payer account is valid. Public for now to support a thin
     * adapter to use in {@code IngestChecker}.
     *
     * TODO - replace all uses of this method with refactored ingest APIs
     *
     * @param payerNum the payer account number
     * @return the status of the payer account
     */
    public ResponseCodeEnum payerAccountStatus(final EntityNum payerNum) {
        return queryableAccountStatus(payerNum, accounts.get());
    }

    public com.hedera.hapi.node.base.ResponseCodeEnum payerAccountStatus2(final EntityNum payerNum) {
        return PbjConverter.toPbj(queryableAccountStatus(payerNum, accounts.get()));
    }

    /**
     * Returns an object summarizing the result of testing if the verified payer
     * account of the given transaction can afford to cover its fees (with the
     * option to include or exclude the service component). Public for now to
     * support a thin adapter to use in {@code IngestChecker}.
     *
     * <p>If the payer account <i>can</i> afford the fees, the returned object
     * will have a status of {@code OK} and a fee requirement of zero. If the
     * payer account <i>cannot</i> afford the fees, the returned object will
     * have a status of {@code INSUFFICIENT_TX_FEE} and the fee amount that
     * would have satisfied the check.
     *
     * @param accessor the accessor for the transaction
     * @param includeSvcFee whether to include the service fee in the check
     * @return the summary of the solvency test
     */
    public TxnValidityAndFeeReq solvencyOfVerifiedPayer(SignedTxnAccessor accessor, boolean includeSvcFee) {
        final var payerId = EntityNum.fromAccountId(accessor.getPayer());
        final var payerAccount = accounts.get().get(payerId);

        try {
            final var now = accessor.getTxnId().getTransactionValidStart();
            final var payerKey = payerAccount.getAccountKey();
            final var estimatedFees = feeCalculator.estimateFee(accessor, payerKey, stateView.get(), now);
            final var estimatedReqFee = totalOf(estimatedFees, includeSvcFee);

            if (accessor.getTxn().getTransactionFee() < estimatedReqFee) {
                return new TxnValidityAndFeeReq(INSUFFICIENT_TX_FEE, estimatedReqFee);
            }

            final var estimatedAdj = Math.min(0L, feeCalculator.estimatedNonFeePayerAdjustments(accessor, now));
            final var requiredPayerBalance = estimatedReqFee - estimatedAdj;
            final var payerBalance = payerAccount.getBalance();
            var finalStatus = OK;
            if (payerBalance < requiredPayerBalance) {
                final var expiryStatus = validator.expiryStatusGiven(
                        payerBalance, payerAccount.isExpiredAndPendingRemoval(), payerAccount.isSmartContract());
                finalStatus = expiryStatus != OK ? expiryStatus : INSUFFICIENT_PAYER_BALANCE;
            }

            return new TxnValidityAndFeeReq(finalStatus, estimatedReqFee);
        } catch (Exception suspicious) {
            log.warn("Fee calculation failure may be justifiable due to an expiring payer, but...", suspicious);
            return LOST_PAYER_EXPIRATION_RACE;
        }
    }

    private long totalOf(FeeObject fees, boolean includeSvcFee) {
        return (includeSvcFee ? fees.serviceFee() : 0) + fees.nodeFee() + fees.networkFee();
    }

    private ResponseCodeEnum checkSigs(SignedTxnAccessor accessor) {
        try {
            return precheckVerifier.hasNecessarySignatures(accessor) ? OK : INVALID_SIGNATURE;
        } catch (KeyPrefixMismatchException ignore) {
            return KEY_PREFIX_MISMATCH;
        } catch (InvalidAccountIDException ignore) {
            return INVALID_ACCOUNT_ID;
        } catch (Exception ignore) {
            return INVALID_SIGNATURE;
        }
    }
}
