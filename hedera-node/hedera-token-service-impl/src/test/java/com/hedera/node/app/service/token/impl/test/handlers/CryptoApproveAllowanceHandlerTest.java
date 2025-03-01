/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.handlers.CryptoApproveAllowanceHandler;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class CryptoApproveAllowanceHandlerTest extends CryptoHandlerTestBase {
    private final TokenID nft = TokenID.newBuilder().tokenNum(56789).build();
    private final TokenID token = TokenID.newBuilder().tokenNum(6789).build();
    private final AccountID spender = AccountID.newBuilder().accountNum(12345).build();
    private final AccountID delegatingSpender =
            AccountID.newBuilder().accountNum(1234567).build();
    private final AccountID owner = AccountID.newBuilder().accountNum(123456).build();
    private final HederaKey ownerKey = asHederaKey(A_COMPLEX_KEY).get();

    @Mock
    private MerkleAccount ownerAccount;

    private final CryptoAllowance cryptoAllowance = CryptoAllowance.newBuilder()
            .spender(spender)
            .owner(owner)
            .amount(10L)
            .build();
    private final TokenAllowance tokenAllowance = TokenAllowance.newBuilder()
            .spender(spender)
            .amount(10L)
            .tokenId(token)
            .owner(owner)
            .build();

    private final NftAllowance nftAllowance = NftAllowance.newBuilder()
            .spender(spender)
            .owner(owner)
            .tokenId(nft)
            .approvedForAll(Boolean.TRUE)
            .serialNumbers(List.of(1L, 2L))
            .build();
    private final NftAllowance nftAllowanceWithDelegatingSpender = NftAllowance.newBuilder()
            .spender(spender)
            .owner(owner)
            .tokenId(nft)
            .approvedForAll(Boolean.FALSE)
            .serialNumbers(List.of(1L, 2L))
            .delegatingSpender(delegatingSpender)
            .build();

    private CryptoApproveAllowanceHandler subject = new CryptoApproveAllowanceHandler();

    @Test
    void cryptoApproveAllowanceVanilla() {
        given(accounts.get(EntityNumVirtualKey.fromLong(owner.accountNum()))).willReturn(ownerAccount);
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(payer, false);
        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);
        basicMetaAssertions(context, 3, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(List.of(ownerKey, ownerKey, ownerKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceFailsWithInvalidOwner() {
        given(accounts.get(EntityNumVirtualKey.fromLong(owner.accountNum()))).willReturn(null);

        final var txn = cryptoApproveAllowanceTransaction(payer, false);
        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);
        basicMetaAssertions(context, 0, true, INVALID_ALLOWANCE_OWNER_ID);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(List.of(), context.getRequiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceDoesntAddIfOwnerSameAsPayer() {
        given(accounts.get(EntityNumVirtualKey.fromLong(owner.accountNum()))).willReturn(ownerAccount);
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);

        final var txn = cryptoApproveAllowanceTransaction(owner, false);
        final var context = new PreHandleContext(store, txn, owner);
        subject.preHandle(context);
        basicMetaAssertions(context, 0, false, OK);
        assertEquals(ownerKey, context.getPayerKey());
        assertIterableEquals(List.of(), context.getRequiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceAddsDelegatingSpender() {
        given(accounts.get(EntityNumVirtualKey.fromLong(owner.accountNum()))).willReturn(ownerAccount);
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
        given(accounts.get(EntityNumVirtualKey.fromLong(delegatingSpender.accountNum())))
                .willReturn(payerAccount);

        final var txn = cryptoApproveAllowanceTransaction(payer, true);
        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);
        basicMetaAssertions(context, 3, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(List.of(ownerKey, ownerKey, payerKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void cryptoApproveAllowanceFailsIfDelegatingSpenderMissing() {
        given(accounts.get(EntityNumVirtualKey.fromLong(owner.accountNum()))).willReturn(ownerAccount);
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);
        given(accounts.get(EntityNumVirtualKey.fromLong(delegatingSpender.accountNum())))
                .willReturn(null);

        final var txn = cryptoApproveAllowanceTransaction(payer, true);
        final var context = new PreHandleContext(store, txn, payer);
        subject.preHandle(context);
        assertEquals(payerKey, context.getPayerKey());
        basicMetaAssertions(context, 2, true, INVALID_DELEGATING_SPENDER);
        assertIterableEquals(List.of(ownerKey, ownerKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle());
    }

    private TransactionBody cryptoApproveAllowanceTransaction(
            final AccountID id, final boolean isWithDelegatingSpender) {
        final var transactionID = TransactionID.newBuilder().accountID(id).transactionValidStart(consensusTimestamp);
        final var allowanceTxnBody = CryptoApproveAllowanceTransactionBody.newBuilder()
                .cryptoAllowances(cryptoAllowance)
                .tokenAllowances(tokenAllowance)
                .nftAllowances(isWithDelegatingSpender ? nftAllowanceWithDelegatingSpender : nftAllowance)
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoApproveAllowance(allowanceTxnBody)
                .build();
    }
}
