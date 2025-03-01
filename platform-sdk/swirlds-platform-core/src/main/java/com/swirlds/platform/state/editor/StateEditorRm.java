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

package com.swirlds.platform.state.editor;

import static com.swirlds.platform.state.editor.StateEditorUtils.formatNode;
import static com.swirlds.platform.state.editor.StateEditorUtils.formatParent;

import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.route.MerkleRouteIterator;
import picocli.CommandLine;

@CommandLine.Command(
        name = "rm",
        mixinStandardHelpOptions = true,
        description = "Remove a node, replacing it with null.")
@SubcommandOf(StateEditorRoot.class)
public class StateEditorRm extends StateEditorOperation {

    private String path = "";

    @CommandLine.Parameters(arity = "0..1", description = "The route of the node to remove.")
    private void setPath(final String path) {
        this.path = path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        final StateEditor.ParentInfo parentInfo = getStateEditor().getParentInfo(path);
        final MerkleRoute destinationRoute = parentInfo.target();
        final MerkleInternal parent = parentInfo.parent();
        final int indexInParent = parentInfo.indexInParent();

        final MerkleNode child = getStateEditor().getState().getNodeAtRoute(destinationRoute);

        System.out.println("Removing " + formatNode(child) + " from parent " + formatParent(parent, indexInParent));

        parent.setChild(indexInParent, null);

        // Invalidate hashes in path down from root
        new MerkleRouteIterator(getStateEditor().getState(), parent.getRoute())
                .forEachRemaining(Hashable::invalidateHash);
    }
}
