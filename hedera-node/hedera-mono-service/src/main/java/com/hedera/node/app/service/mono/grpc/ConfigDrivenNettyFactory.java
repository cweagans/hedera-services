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

package com.hedera.node.app.service.mono.grpc;

import static com.hedera.node.app.spi.config.Profile.DEV;
import static com.hedera.node.app.spi.config.Profile.PROD;
import static io.netty.handler.ssl.SupportedCipherSuiteFilter.INSTANCE;

import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.SSLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ConfigDrivenNettyFactory implements NettyBuilderFactory {

    private static final Logger log = LogManager.getLogger(ConfigDrivenNettyFactory.class);

    private static final List<String> SUPPORTED_CIPHERS = List.of(
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_AES_256_GCM_SHA384");
    private static final List<String> SUPPORTED_PROTOCOLS = List.of("TLSv1.2", "TLSv1.3");

    private final NodeLocalProperties nodeProperties;

    @Inject
    public ConfigDrivenNettyFactory(final NodeLocalProperties nodeProperties) {
        this.nodeProperties = nodeProperties;
    }

    @Override
    public NettyServerBuilder builderFor(final int port, final boolean sslEnabled)
            throws FileNotFoundException, SSLException {
        final var activeProfile = nodeProperties.activeProfile();
        final var nettyMode = (activeProfile == DEV) ? DEV : nodeProperties.nettyMode();

        log.info(
                "Configuring a Netty server on port {} (TLS {}) for {} environment",
                port,
                (sslEnabled ? "ON" : "OFF"),
                nettyMode);

        final var builder = NettyServerBuilder.forPort(port);
        if (nettyMode == PROD) {
            configureProd(builder);
        }
        if (sslEnabled) {
            configureTls(builder);
        }

        return builder;
    }

    private void configureProd(final NettyServerBuilder builder) {
        builder.keepAliveTime(nodeProperties.nettyProdKeepAliveTime(), TimeUnit.SECONDS)
                .permitKeepAliveTime(nodeProperties.nettyProdKeepAliveTime(), TimeUnit.SECONDS)
                .keepAliveTimeout(nodeProperties.nettyProdKeepAliveTimeout(), TimeUnit.SECONDS)
                .maxConnectionAge(nodeProperties.nettyMaxConnectionAge(), TimeUnit.SECONDS)
                .maxConnectionAgeGrace(nodeProperties.nettyMaxConnectionAgeGrace(), TimeUnit.SECONDS)
                .maxConnectionIdle(nodeProperties.nettyMaxConnectionIdle(), TimeUnit.SECONDS)
                .maxConcurrentCallsPerConnection(nodeProperties.nettyMaxConcurrentCalls())
                .flowControlWindow(nodeProperties.nettyFlowControlWindow())
                .directExecutor()
                .channelType(EpollServerSocketChannel.class)
                .bossEventLoopGroup(new EpollEventLoopGroup())
                .workerEventLoopGroup(new EpollEventLoopGroup());
    }

    private void configureTls(final NettyServerBuilder builder) throws SSLException, FileNotFoundException {
        final var crt = new File(nodeProperties.nettyTlsCrtPath());
        if (!crt.exists()) {
            log.warn("Specified TLS cert '{}' doesn't exist!", nodeProperties.nettyTlsCrtPath());
            throw new FileNotFoundException(nodeProperties.nettyTlsCrtPath());
        }
        final var key = new File(nodeProperties.nettyTlsKeyPath());
        if (!key.exists()) {
            log.warn("Specified TLS key '{}' doesn't exist!", nodeProperties.nettyTlsKeyPath());
            throw new FileNotFoundException(nodeProperties.nettyTlsKeyPath());
        }
        final var sslContext = GrpcSslContexts.configure(SslContextBuilder.forServer(crt, key))
                .protocols(SUPPORTED_PROTOCOLS)
                .ciphers(SUPPORTED_CIPHERS, INSTANCE)
                .build();
        builder.sslContext(sslContext);
    }
}
