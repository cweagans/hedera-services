/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.api.validation;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An exceptions that wraps collection of violations
 */
public class ConfigViolationException extends IllegalStateException {

    private final List<ConfigViolation> violations;

    /**
     * Creates a new instance based on violations
     *
     * @param message
     * 		message of the exception
     * @param violations
     * 		the violations
     */
    public ConfigViolationException(final String message, final List<ConfigViolation> violations) {
        super(message);
        Objects.requireNonNull(violations, "violations should not be null");
        this.violations = Collections.unmodifiableList(violations);
    }

    /**
     * Returns the immutable list of violations
     *
     * @return the list of violations
     */
    public List<ConfigViolation> getViolations() {
        return violations;
    }
}
