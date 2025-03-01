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

package com.hedera.node.app.service.evm.store.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount;
import com.hedera.node.app.service.evm.store.contracts.WorldStateAccount;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateTrackingAccountTest {
    private static final long newBalance = 200_000L;
    private static final long initialBalance = 100_000L;
    private static final Address targetAddress = Address.fromHexString("0x000000000000000000000000000000000000066e");

    @Mock
    private HederaEvmEntityAccess entityAccess;

    private AbstractCodeCache codeCache;

    @BeforeEach
    void setUp() {
        codeCache = new AbstractCodeCache(12, entityAccess);
    }

    //    void mirrorsBalanceChangesInNonNullTrackingAccounts()

    @Test
    void missingWrappedAccountIsNotATokenProxy() {
        final var subject = new UpdateTrackingAccount<>(targetAddress, null);

        assertFalse(subject.wrappedAccountIsTokenProxy());
    }

    @Test
    void wrappedTokenProxyIsRecognized() {
        final var tokenProxyAccount = new HederaEvmWorldStateTokenAccount(targetAddress);

        final var subject = new UpdateTrackingAccount<>(tokenProxyAccount, null);

        assertTrue(subject.wrappedAccountIsTokenProxy());
    }

    @Test
    void justPropagatesBalanceChangeWithNullTrackingAccounts() {
        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);

        final var subject = new UpdateTrackingAccount<>(account, null);

        subject.setBalance(Wei.of(newBalance));

        assertEquals(newBalance, subject.getBalance().toLong());
        assertFalse(subject.wrappedAccountIsTokenProxy());
    }

    @Test
    void reusesAddressHashWhenConstructedWithTracker() {
        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);

        final var base = new UpdateTrackingAccount<>(account, null);
        final var subject = new UpdateTrackingAccount<>(base, null);
        assertSame(subject.getAddressHash(), base.getAddressHash());
    }

    @Test
    void recognizesUpdatedCode() {
        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);

        final var subject = new UpdateTrackingAccount<>(account, null);

        assertFalse(subject.codeWasUpdated());
        subject.setCode(Bytes.minimalBytes(1234L));
        assertTrue(subject.codeWasUpdated());
    }

    @Test
    void getsWrappedCodeHashIfConstructedWithAccount() {
        final var mockCode = Bytes.minimalBytes(4321L);
        given(entityAccess.fetchCodeIfPresent(targetAddress)).willReturn(mockCode);

        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);

        final var subject = new UpdateTrackingAccount<>(account, null);

        assertEquals(Hash.hash(mockCode), subject.getCodeHash());
    }

    @Test
    void reusesComputedHashOfUpdatedCode() {
        final var mockCode = Bytes.minimalBytes(4321L);

        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);

        final var subject = new UpdateTrackingAccount<>(account, null);
        subject.setCode(mockCode);

        final var firstCodeHash = subject.getCodeHash();
        final var secondCodeHash = subject.getCodeHash();
        assertEquals(Hash.hash(mockCode), firstCodeHash);
        assertSame(firstCodeHash, secondCodeHash);
    }

    @Test
    void hasCodeDelegatesToWrappedIfNotUpdated() {
        given(entityAccess.fetchCodeIfPresent(targetAddress)).willReturn(Bytes.EMPTY);

        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);
        final var subject = new UpdateTrackingAccount<>(account, null);

        assertFalse(subject.hasCode());
    }

    @Test
    void hasCodeUsesUpdatedCodeIfSet() {
        final var subject = new UpdateTrackingAccount<>(targetAddress, null);

        assertFalse(subject.hasCode());
        subject.setCode(Bytes.minimalBytes(1234L));
        assertTrue(subject.hasCode());
    }

    @Test
    void doesNotSupportStreamingStorageEntries() {
        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);
        final var subject = new UpdateTrackingAccount<>(account, null);

        assertThrows(
                UnsupportedOperationException.class, () -> subject.storageEntriesFrom(Bytes32.ZERO, Integer.MAX_VALUE));
    }

    @Test
    void canClearStorage() {
        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);
        final var subject = new UpdateTrackingAccount<>(account, null);

        subject.setStorageValue(UInt256.ONE, UInt256.ONE);
        assertFalse(subject.getStorageWasCleared());
        subject.clearStorage();
        assertTrue(subject.getStorageWasCleared());
        assertTrue(subject.getUpdatedStorage().isEmpty());
    }

    @Test
    void setBalanceOkWithNullTrackingAccounts() {
        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);
        final var subject = new UpdateTrackingAccount<>(account, null);

        subject.setBalance(Wei.of(Long.MAX_VALUE));

        assertEquals(Long.MAX_VALUE, subject.getBalance().toLong());
    }

    @Test
    void getStorageValueRecognizesUpdatedStorage() {
        final var mockValue = UInt256.valueOf(1_234_567L);
        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);
        final var subject = new UpdateTrackingAccount<>(account, null);

        subject.setStorageValue(UInt256.ONE, mockValue);
        assertSame(mockValue, subject.getStorageValue(UInt256.ONE));
    }

    @Test
    void getStorageValueRecognizesClearedStorage() {
        final var mockValue = UInt256.valueOf(1_234_567L);
        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);
        final var subject = new UpdateTrackingAccount<>(account, null);

        subject.setStorageValue(UInt256.ONE, mockValue);
        subject.clearStorage();
        assertSame(UInt256.ZERO, subject.getStorageValue(UInt256.ONE));
    }

    @Test
    void nonTrackingAccountAlwaysReturnsZeroStorageValue() {
        final var subject = new UpdateTrackingAccount<>(targetAddress, null);

        assertSame(UInt256.ZERO, subject.getStorageValue(UInt256.ONE));
    }

    @Test
    void trackingAccountDelegatesToNonUpdatedStorage() {
        final var mockValue = UInt256.valueOf(1_234_567L);
        given(entityAccess.getStorage(targetAddress, UInt256.ONE)).willReturn(mockValue);

        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);
        final var subject = new UpdateTrackingAccount<>(account, null);

        assertSame(mockValue, subject.getStorageValue(UInt256.ONE));
    }

    @Test
    void trackingAccountDelegatesToGetOriginalStorage() {
        final var mockValue = UInt256.valueOf(1_234_567L);
        given(entityAccess.getStorage(targetAddress, UInt256.ONE)).willReturn(mockValue);

        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);
        final var subject = new UpdateTrackingAccount<>(account, null);

        assertSame(mockValue, subject.getOriginalStorageValue(UInt256.ONE));
    }

    @Test
    void clearedTrackingAccountDelegatesToGetOriginalStorage() {
        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);
        final var subject = new UpdateTrackingAccount<>(account, null);
        subject.clearStorage();

        assertSame(UInt256.ZERO, subject.getOriginalStorageValue(UInt256.ONE));
    }

    @Test
    void nonTrackingAlwaysHasZeroOriginalStorage() {
        final var subject = new UpdateTrackingAccount<>(targetAddress, null);
        assertSame(UInt256.ZERO, subject.getOriginalStorageValue(UInt256.ONE));
    }

    @Test
    void getMutableReturnsSelf() {
        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);
        final var subject = new UpdateTrackingAccount<>(account, null);

        assertSame(subject, subject.getMutable());
    }

    @Test
    void toStringWorksAsExpected() {
        final var expectedNoUpdatedStorageOrCode = "0x000000000000000000000000000000000000066e -> {nonce:0,"
                + " balance:0x00000000000000000000000000000000000000000000000000000000000186a0,"
                + " code:[not updated], storage:[not updated] }";
        final var expectedUpdatedStorageNotCode = "0x000000000000000000000000000000000000066e -> {nonce:0,"
                + " balance:0x00000000000000000000000000000000000000000000000000000000000186a0,"
                + " code:[not updated], "
                + "storage:{0x0000000000000000000000000000000000000000000000000000000000000001=0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff}"
                + " }";
        final var expectedUpdatedCodeClearedStorage = "0x000000000000000000000000000000000000066e -> {nonce:0,"
                + " balance:0x00000000000000000000000000000000000000000000000000000000000186a0,"
                + " code:0x04d2, storage:[cleared] }";

        final var account = new WorldStateAccount(targetAddress, Wei.of(initialBalance), codeCache, entityAccess);
        final var subject = new UpdateTrackingAccount<>(account, null);

        assertEquals(expectedNoUpdatedStorageOrCode, subject.toString());

        subject.setStorageValue(UInt256.ONE, UInt256.MAX_VALUE);
        assertEquals(expectedUpdatedStorageNotCode, subject.toString());

        subject.setCode(Bytes.minimalBytes(1_234L));
        subject.clearStorage();
        assertEquals(expectedUpdatedCodeClearedStorage, subject.toString());
    }
}
