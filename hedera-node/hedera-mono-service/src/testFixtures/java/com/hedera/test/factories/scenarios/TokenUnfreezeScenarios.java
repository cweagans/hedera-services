/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.test.factories.scenarios;

import static com.hedera.test.factories.txns.TokenUnfreezeFactory.newSignedTokenUnfreeze;

import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;

public enum TokenUnfreezeScenarios implements TxnHandlingScenario {
    VALID_UNFREEZE_WITH_EXTANT_TOKEN {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(newSignedTokenUnfreeze()
                    .unfreezing(KNOWN_TOKEN_WITH_FREEZE)
                    .nonPayerKts(TOKEN_FREEZE_KT)
                    .get());
        }
    },
    UNFREEZE_WITH_MISSING_FREEZE_TOKEN {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(newSignedTokenUnfreeze().get());
        }
    },
    UNFREEZE_WITH_INVALID_TOKEN {
        @Override
        public PlatformTxnAccessor platformTxn() throws Throwable {
            return PlatformTxnAccessor.from(
                    newSignedTokenUnfreeze().unfreezing(MISSING_TOKEN).get());
        }
    },
}
