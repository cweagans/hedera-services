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

package com.hedera.node.app.service.util.impl.test;

import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.spi.state.SchemaRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UtilServiceImplTest {
    @Mock
    private SchemaRegistry registry;

    @Test
    void testSpi() {
        // when
        final UtilService service = UtilService.getInstance();

        // then
        Assertions.assertNotNull(service, "We must always receive an instance");
        Assertions.assertEquals(
                UtilServiceImpl.class,
                service.getClass(),
                "We must always receive an instance of type " + UtilServiceImpl.class.getName());
    }

    @Test
    void registersExpectedSchema() {
        final var subject = UtilService.getInstance();

        subject.registerSchemas(registry);
        verifyNoInteractions(registry);
    }
}
