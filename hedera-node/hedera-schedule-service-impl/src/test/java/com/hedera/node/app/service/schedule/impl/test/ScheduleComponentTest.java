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

package com.hedera.node.app.service.schedule.impl.test;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.schedule.impl.components.DaggerScheduleComponent;
import com.hedera.node.app.service.schedule.impl.components.ScheduleComponent;
import org.junit.jupiter.api.Test;

class ScheduleComponentTest {

    @Test
    void objectGraphRootsAreAvailable() {
        // given:
        ScheduleComponent subject = DaggerScheduleComponent.factory().create();

        // expect:
        assertNotNull(subject.scheduleCreateHandler());
        assertNotNull(subject.scheduleDeleteHandler());
        assertNotNull(subject.scheduleGetInfoHandler());
        assertNotNull(subject.scheduleSignHandler());
    }
}
