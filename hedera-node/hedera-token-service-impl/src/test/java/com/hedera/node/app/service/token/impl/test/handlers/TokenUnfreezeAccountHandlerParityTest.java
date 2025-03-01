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

import static com.hedera.node.app.service.token.impl.test.handlers.AdapterUtils.txnFrom;
import static com.hedera.node.app.service.token.impl.test.util.MetaAssertion.basicContextAssertions;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.UNFREEZE_WITH_INVALID_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.UNFREEZE_WITH_MISSING_FREEZE_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.VALID_UNFREEZE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestoredToPbj;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenUnfreezeAccountHandlerParityTest {
    private AccountAccess accountStore;
    private ReadableTokenStore tokenStore;
    private TokenUnfreezeAccountHandler subject;

    @BeforeEach
    void setUp() {
        accountStore = AdapterUtils.wellKnownKeyLookupAt();
        tokenStore = SigReqAdapterUtils.wellKnownTokenStoreAt();
        subject = new TokenUnfreezeAccountHandler();
    }

    @Test
    void tokenUnfreezeWithExtantFreezable() {
        final var txn = txnFrom(VALID_UNFREEZE_WITH_EXTANT_TOKEN);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context, tokenStore);

        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(sanityRestoredToPbj(context.getRequiredNonPayerKeys()), contains(TOKEN_FREEZE_KT.asPbjKey()));
        basicContextAssertions(context, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenUnfreezeMissingToken() {
        final var txn = txnFrom(UNFREEZE_WITH_MISSING_FREEZE_TOKEN);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context, tokenStore);

        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(sanityRestoredToPbj(context.getRequiredNonPayerKeys()).isEmpty());
        basicContextAssertions(context, 0, true, ResponseCodeEnum.INVALID_TOKEN_ID);
    }

    @Test
    void tokenUnfreezeWithInvalidToken() {
        final var txn = txnFrom(UNFREEZE_WITH_INVALID_TOKEN);

        final var context = new PreHandleContext(accountStore, txn);
        subject.preHandle(context, tokenStore);

        assertEquals(sanityRestoredToPbj(context.getPayerKey()), DEFAULT_PAYER_KT.asPbjKey());
        basicContextAssertions(context, 0, true, ResponseCodeEnum.INVALID_TOKEN_ID);
    }
}
