/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.stream.Signer;

/**
 * An interface for Swirlds Platform.
 */
public interface Platform extends PlatformIdentity, StateAccessor, Signer, TransactionSubmitter {

    /**
     * Get the platform context, which contains various utilities and services provided by the platform.
     *
     * @return this node's platform context
     */
    PlatformContext getContext();

    /**
     * Get the notification engine running on this node.
     *
     * @return a notification engine
     */
    NotificationEngine getNotificationEngine();

    /**
     * Get the transactionMaxBytes in Settings
     *
     * @return integer representing the maximum number of bytes allowed in a transaction
     * @deprecated access "transactionMaxBytes" configuration directly instead of using this method
     */
    @Deprecated(forRemoval = true)
    static int getTransactionMaxBytes() {
        return SettingsCommon.transactionMaxBytes;
    }
}
