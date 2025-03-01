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

package com.swirlds.common.exceptions;

/**
 * This exception is thrown when an operation violates mutability constraints.
 */
public class MutabilityException extends RuntimeException {

    public MutabilityException() {}

    public MutabilityException(final String message) {
        super(message);
    }

    public MutabilityException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public MutabilityException(final Throwable cause) {
        super(cause);
    }

    public MutabilityException(
            final String message,
            final Throwable cause,
            final boolean enableSuppression,
            final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
