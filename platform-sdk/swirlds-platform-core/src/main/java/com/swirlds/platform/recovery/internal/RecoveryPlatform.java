/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.recovery.internal;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.crypto.CryptoSetup.initNodeSecurity;

import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.context.internal.DefaultPlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import com.swirlds.common.metrics.platform.MetricKeyRegistry;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.state.signed.SignedState;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A simplified version of the platform to be used during the recovery workflow.
 */
public class RecoveryPlatform implements Platform, AutoCloseableNonThrowing {

    private final NodeId selfId;

    private final AddressBook addressBook;
    private final Crypto crypto;

    private SignedState immutableState;

    private final Metrics metrics;

    private final ScheduledExecutorService metricsExecutor;

    private final NotificationEngine notificationEngine;

    private final PlatformContext context;

    /**
     * Create a new recovery platform.
     *
     * @param configuration the node's configuration
     * @param initialState  the starting signed state
     * @param selfId        the ID of the node
     */
    public RecoveryPlatform(final Configuration configuration, final SignedState initialState, final long selfId) {

        this.selfId = new NodeId(false, selfId);

        this.addressBook = initialState.getAddressBook();

        crypto = initNodeSecurity(addressBook, configuration)[(int) selfId];

        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);

        metricsExecutor = Executors.newSingleThreadScheduledExecutor(
                getStaticThreadManager().createThreadFactory("recovery-platform", "MetricsThread"));
        metrics = new DefaultMetrics(
                this.selfId, new MetricKeyRegistry(), metricsExecutor, new DefaultMetricsFactory(), metricsConfig);
        metrics.start();

        notificationEngine = NotificationEngine.buildEngine(getStaticThreadManager());

        context = new DefaultPlatformContext(configuration, metrics, CryptographyHolder.get());

        setLatestState(initialState);
    }

    /**
     * Set the most recent immutable state.
     *
     * @param signedState the most recent signed state
     */
    public synchronized void setLatestState(final SignedState signedState) {
        if (this.immutableState != null) {
            immutableState.release();
        }
        signedState.reserve();
        this.immutableState = signedState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signature sign(final byte[] data) {
        return crypto.sign(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlatformContext getContext() {
        return context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NotificationEngine getNotificationEngine() {
        return notificationEngine;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AddressBook getAddressBook() {
        return addressBook;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeId getSelfId() {
        return selfId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getInstanceNumber() {
        // This never runs in the same JVM with other platform instances, so it's always instance 0.
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public synchronized <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState() {
        if (immutableState == null) {
            return null;
        }
        immutableState.reserve();
        return new AutoCloseableWrapper<>((T) immutableState.getSwirldState(), immutableState::release);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestSignedState() {
        if (immutableState == null) {
            return null;
        }
        return (AutoCloseableWrapper<T>) immutableState.getSwirldState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean createTransaction(final byte[] trans) {
        // Transaction creation always fails
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (immutableState != null) {
            immutableState.release();
        }
        metricsExecutor.shutdown();
        notificationEngine.shutdown();
    }
}
