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

package com.hedera.node.app.service.mono.context.properties;

import static com.hedera.node.app.spi.config.PropertyNames.GRPC_PORT;
import static com.hedera.node.app.spi.config.PropertyNames.GRPC_TLS_PORT;
import static com.hedera.node.app.spi.config.PropertyNames.GRPC_WORKFLOWS_PORT;
import static com.hedera.node.app.spi.config.PropertyNames.GRPC_WORKFLOWS_TLS_PORT;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_PROFILES_ACTIVE;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.node.app.spi.config.Profile;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScreenedNodeFilePropsTest {

    Logger log;

    ScreenedNodeFileProps subject;

    private final String STD_NODE_PROPS_LOC = "src/test/resources/bootstrap/node.properties";
    private final String EMPTY_NODE_PROPS_LOC = "src/test/resources/bootstrap/empty-override.properties";
    private final String BROKEN_NODE_PROPS_LOC = "src/test/resources/bootstrap/broken-node.properties";
    private final String LEGACY_NODE_PROPS_LOC = "src/test/resources/bootstrap/legacy-node.properties";

    private static final Map<String, Object> expectedProps = Map.ofEntries(
            entry(GRPC_WORKFLOWS_PORT, 80211),
            entry(GRPC_WORKFLOWS_TLS_PORT, 80212),
            entry(GRPC_PORT, 70211),
            entry(GRPC_TLS_PORT, 40212),
            entry(HEDERA_PROFILES_ACTIVE, Profile.TEST));

    @BeforeEach
    void setup() {
        log = mock(Logger.class);
        ScreenedNodeFileProps.log = log;
        ScreenedNodeFileProps.nodePropsLoc = STD_NODE_PROPS_LOC;
        ScreenedNodeFileProps.legacyNodePropsLoc = LEGACY_NODE_PROPS_LOC;

        subject = new ScreenedNodeFileProps();
    }

    @Test
    void warnsOfFailedTransform() {
        // setup:
        ScreenedNodeFileProps.nodePropsLoc = BROKEN_NODE_PROPS_LOC;
        ScreenedNodeFileProps.legacyNodePropsLoc = EMPTY_NODE_PROPS_LOC;

        // given:
        subject = new ScreenedNodeFileProps();

        // expect:
        verify(log)
                .warn(String.format(
                        ScreenedNodeFileProps.UNTRANSFORMABLE_PROP_TPL,
                        "asdf",
                        "environment",
                        "NumberFormatException"));
        // and:
        assertTrue(subject.getFromFile().isEmpty());
    }

    @Test
    void warnsOfUnparseableAndDeprecated() {
        // expect:
        verify(log)
                .warn(String.format(
                        ScreenedNodeFileProps.DEPRECATED_PROP_TPL, "tlsPort", "grpc.tlsPort", STD_NODE_PROPS_LOC));
        // and:
        verify(log)
                .warn(String.format(
                        ScreenedNodeFileProps.UNPARSEABLE_PROP_TPL, "ABCDEF", "grpc.tlsPort", "NumberFormatException"));
    }

    @Test
    void ignoresNonNodeProps() {
        // expect:
        verify(log).warn(String.format(ScreenedNodeFileProps.MISPLACED_PROP_TPL, "hedera.shard"));
    }

    @Test
    void hasExpectedProps() {
        // expect:
        for (final String name : expectedProps.keySet()) {
            assertTrue(subject.containsProperty(name), "Should have '" + name + "'!");
            assertEquals(expectedProps.get(name), subject.getProperty(name));
        }
        // and:
        assertEquals(expectedProps, subject.getFromFile());
        // and:
        assertEquals(expectedProps.keySet(), subject.allPropertyNames());
    }
}
