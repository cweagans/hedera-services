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

package com.hedera.node.app.workflows.dispatcher;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.state.HederaState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableStoreFactoryTest {
    private ReadableStoreFactory subject;

    @Mock
    private HederaState state;

    @Mock
    ReadableStates readableStates;

    @BeforeEach
    void setUp() {
        subject = new ReadableStoreFactory(state);
    }

    @Test
    void returnsTopicStore() {
        given(state.createReadableStates("ConsensusService")).willReturn(readableStates);
        final var store = subject.createTopicStore();
        assertNotNull(store);
    }
}
