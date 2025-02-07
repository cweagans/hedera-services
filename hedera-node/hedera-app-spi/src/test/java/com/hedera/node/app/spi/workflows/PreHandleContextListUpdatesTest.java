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

package com.hedera.node.app.spi.workflows;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleContextListUpdatesTest {
    public static final Key A_COMPLEX_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    Key.newBuilder()
                                            .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                                            .build(),
                                    Key.newBuilder()
                                            .ed25519(Bytes.wrap("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
                                            .build())))
            .build();
    private Timestamp consensusTimestamp =
            Timestamp.newBuilder().seconds(1_234_567L).build();
    private Key key = A_COMPLEX_KEY;
    private AccountID payer = AccountID.newBuilder().accountNum(3L).build();
    private Long payerNum = 3L;

    @Mock
    private HederaKey payerKey;

    final AccountID otherAccountId = AccountID.newBuilder().accountNum(12345L).build();
    final ContractID otherContractId =
            ContractID.newBuilder().contractNum(123456L).build();

    @Mock
    private HederaKey otherKey;

    @Mock
    private AccountAccess accountAccess;

    private PreHandleContext subject;

    @BeforeEach
    void setUp() {}

    @Test
    void gettersWorkAsExpectedWhenOnlyPayerKeyExist() {
        final var txn = createAccountTransaction();
        given(accountAccess.getKey(payer)).willReturn(withKey(payerKey));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer);

        assertFalse(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(payerKey, subject.getPayerKey());
        assertEquals(List.of(), subject.getRequiredNonPayerKeys());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void nullInputToBuilderArgumentsThrows() {
        given(accountAccess.getKey(payer)).willReturn(withKey(payerKey));
        final var subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer);
        assertThrows(NullPointerException.class, () -> new PreHandleContext(null, createAccountTransaction(), payer));
        assertThrows(NullPointerException.class, () -> new PreHandleContext(accountAccess, null, payer));
        assertThrows(
                NullPointerException.class,
                () -> new PreHandleContext(accountAccess, createAccountTransaction(), (AccountID) null));
        assertThrows(NullPointerException.class, () -> subject.status(null));
        assertThrows(NullPointerException.class, () -> subject.addNonPayerKey((AccountID) null));
        assertThrows(NullPointerException.class, () -> subject.addNonPayerKeyIfReceiverSigRequired(null, null));
        assertDoesNotThrow(() -> subject.addNonPayerKey(payer, null));
        assertDoesNotThrow(() -> subject.addNonPayerKeyIfReceiverSigRequired(payer, null));
    }

    @Test
    void gettersWorkAsExpectedWhenPayerIsSet() {
        final var txn = createAccountTransaction();
        given(accountAccess.getKey(payer)).willReturn(withKey(payerKey));
        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer)
                .addAllReqKeys(List.of(payerKey, otherKey));

        assertFalse(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(payerKey, subject.getPayerKey());
        assertEquals(List.of(payerKey, otherKey), subject.getRequiredNonPayerKeys());
        assertEquals(payer, subject.getPayer());
    }

    @Test
    void gettersWorkAsExpectedWhenOtherSigsExist() {
        final var txn = createAccountTransaction();
        given(accountAccess.getKey(payer)).willReturn(withKey(payerKey));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer).addToReqNonPayerKeys(payerKey);

        assertFalse(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(payerKey, subject.getPayerKey());
        assertEquals(List.of(payerKey), subject.getRequiredNonPayerKeys());
    }

    @Test
    void failsWhenPayerKeyDoesntExist() {
        final var txn = createAccountTransaction();
        given(accountAccess.getKey(payer)).willReturn(withFailureReason(INVALID_PAYER_ACCOUNT_ID));

        subject = new PreHandleContext(accountAccess, txn, payer).addToReqNonPayerKeys(payerKey);

        assertTrue(subject.failed());
        assertNull(subject.getPayerKey());
        assertEquals(INVALID_PAYER_ACCOUNT_ID, subject.getStatus());

        assertEquals(txn, subject.getTxn());
        assertEquals(List.of(), subject.getRequiredNonPayerKeys()); // No other keys are added when payerKey is not
        // added
    }

    @Test
    void doesntAddToReqKeysIfStatus() {
        given(accountAccess.getKey(payer)).willReturn(withFailureReason(INVALID_PAYER_ACCOUNT_ID));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer);
        subject.addToReqNonPayerKeys(payerKey);

        assertEquals(0, subject.getRequiredNonPayerKeys().size());
        assertNull(subject.getPayerKey());
        assertFalse(subject.getRequiredNonPayerKeys().contains(payerKey));
    }

    @Test
    void addsToReqKeysCorrectly() {
        given(accountAccess.getKey(payer)).willReturn(withKey(payerKey));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer);

        assertEquals(0, subject.getRequiredNonPayerKeys().size());
        assertEquals(payerKey, subject.getPayerKey());

        subject.addToReqNonPayerKeys(otherKey);
        assertEquals(1, subject.getRequiredNonPayerKeys().size());
        assertEquals(payerKey, subject.getPayerKey());
        assertTrue(subject.getRequiredNonPayerKeys().contains(otherKey));
    }

    @Test
    void settersWorkCorrectly() {
        given(accountAccess.getKey(payer)).willReturn(withKey(payerKey));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer).status(INVALID_ACCOUNT_ID);
        assertEquals(INVALID_ACCOUNT_ID, subject.getStatus());
    }

    @Test
    void returnsIfGivenKeyIsPayer() {
        given(accountAccess.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKey(payer);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(payer, INVALID_ACCOUNT_ID);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void returnsIfGivenKeyIsInvalidAccountId() {
        given(accountAccess.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKey(AccountID.newBuilder().build());
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(AccountID.newBuilder().build(), INVALID_ACCOUNT_ID);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());

        subject.addNonPayerKey(AccountID.newBuilder().build());
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(AccountID.newBuilder().build(), INVALID_ACCOUNT_ID);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void addsContractIdKey() {
        given(accountAccess.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(accountAccess.getKey(otherContractId)).willReturn(new KeyOrLookupFailureReason(otherKey, null));
        given(accountAccess.getKeyIfReceiverSigRequired(otherContractId))
                .willReturn(new KeyOrLookupFailureReason(otherKey, null));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKey(otherContractId);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(otherKey), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(otherContractId);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(otherKey, otherKey), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void doesntLookupIfMetaIsFailedAlready() {
        given(accountAccess.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        subject.status(INVALID_ACCOUNT_ID);

        subject.addNonPayerKey(otherAccountId);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        subject.status(INVALID_ACCOUNT_ID);

        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        subject.status(INVALID_ACCOUNT_ID);

        subject.addNonPayerKey(otherContractId);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        subject.status(INVALID_CONTRACT_ID);

        subject.addNonPayerKeyIfReceiverSigRequired(otherContractId);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        subject.status(INVALID_CONTRACT_ID);
    }

    @Test
    void looksUpOtherKeysIfMetaIsNotFailedAlready() {
        given(accountAccess.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());

        given(accountAccess.getKey(otherAccountId)).willReturn(new KeyOrLookupFailureReason(otherKey, null));

        subject.addNonPayerKey(otherAccountId);
        assertIterableEquals(List.of(otherKey), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());

        given(accountAccess.getKeyIfReceiverSigRequired(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(otherKey, null));
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(otherKey, otherKey), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void doesntFailForInvalidAccount() {
        given(accountAccess.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer)
                .addNonPayerKey(AccountID.newBuilder().accountNum(0L).build());

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void doesntFailForInvalidContract() {
        given(accountAccess.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void doesntFailForAliasedAccount() {
        final var alias = AccountID.newBuilder().alias(Bytes.wrap("test")).build();
        given(accountAccess.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(accountAccess.getKey(alias)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer).addNonPayerKey(alias);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(payerKey), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void doesntFailForAliasedContract() {
        final var alias = ContractID.newBuilder().evmAddress(Bytes.wrap("test")).build();
        given(accountAccess.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(accountAccess.getKey(alias)).willReturn(new KeyOrLookupFailureReason(otherKey, null));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer).addNonPayerKey(alias);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(otherKey), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void failsForInvalidAlias() {
        final var alias = AccountID.newBuilder().alias(Bytes.wrap("test")).build();
        given(accountAccess.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(accountAccess.getKey(alias)).willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer).addNonPayerKey(alias);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(INVALID_ACCOUNT_ID, subject.getStatus());
    }

    @Test
    void setsDefaultFailureStatusIfFailedStatusIsNull() {
        given(accountAccess.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(accountAccess, createAccountTransaction(), payer);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());

        given(accountAccess.getKey(otherAccountId)).willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));
        subject.addNonPayerKey(otherAccountId);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(INVALID_ACCOUNT_ID, subject.getStatus());

        // only for testing , resetting the status to OK
        subject.status(OK);
        given(accountAccess.getKeyIfReceiverSigRequired(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));
        subject.addNonPayerKey(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, subject.getStatus());

        // only for testing , resetting the status to OK
        subject.status(OK);
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, null);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(INVALID_ACCOUNT_ID, subject.getStatus());

        // only for testing , resetting the status to OK
        subject.status(OK);
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, subject.getStatus());
    }

    private TransactionBody createAccountTransaction() {
        final var transactionID = TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
        final var createTxnBody = CryptoCreateTransactionBody.newBuilder()
                .key(key)
                .receiverSigRequired(true)
                .memo("Create Account")
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoCreateAccount(createTxnBody)
                .build();
    }
}
