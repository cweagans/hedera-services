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

package com.hedera.node.app.service.token.impl.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_DELETE}.
 */
@Singleton
public class TokenDeleteHandler implements TransactionHandler {
    @Inject
    public TokenDeleteHandler() {
        // Exists for injection
    }

    /**
     * Pre-handles a {@link HederaFunctionality#TOKEN_DELETE} transaction, returning the metadata required to, at
     * minimum, validate the signatures of all required signing keys.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to change.
     *
     * @param context the {@link PreHandleContext} which collects all information
     *
     * @param tokenStore the {@link ReadableTokenStore} to use to resolve token metadata
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context, @NonNull final ReadableTokenStore tokenStore) {
        requireNonNull(context);
        final var op = context.getTxn().tokenDeletionOrThrow();
        final var tokenId = op.tokenOrElse(TokenID.DEFAULT);
        final var tokenMeta = tokenStore.getTokenMeta(tokenId);
        if (tokenMeta.failed()) {
            context.status(tokenMeta.failureReason());
        } else {
            final var tokenMetadata = tokenMeta.metadata();
            final var adminKey = tokenMetadata.adminKey();
            // we will fail in handle() if token has no admin key
            adminKey.ifPresent(context::addToReqNonPayerKeys);
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
