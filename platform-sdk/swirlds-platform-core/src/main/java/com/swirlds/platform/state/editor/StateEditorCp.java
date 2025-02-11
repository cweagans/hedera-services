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
import static com.swirlds.platform.state.editor.StateEditorUtils.formatRoute;

import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.copy.MerkleCopy;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.route.MerkleRouteIterator;
import picocli.CommandLine;

@CommandLine.Command(
        name = "cp",
        mixinStandardHelpOptions = true,
        description = "Copy a node from one location to another.")
@SubcommandOf(StateEditorRoot.class)
public class StateEditorCp extends StateEditorOperation {

    private String sourcePath;
    private String destinationPath = "";

    @CommandLine.Parameters(index = "0", description = "The route of the node to be copied.")
    private void setSourcePath(final String sourcePath) {
        this.sourcePath = sourcePath;
    }

    @CommandLine.Parameters(index = "1", arity = "0..1", description = "The route where the node should be copied to.")
    private void setDestinationPath(final String destinationPath) {
        this.destinationPath = destinationPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        final StateEditor.ParentInfo parentInfo = getStateEditor().getParentInfo(destinationPath);
        final MerkleRoute destinationRoute = parentInfo.target();
        final MerkleInternal parent = parentInfo.parent();
        final int indexInParent = parentInfo.indexInParent();

        final MerkleNode source = getStateEditor().getRelativeNode(sourcePath);

        System.out.println("Copying " + formatNode(source) + " to " + formatRoute(destinationRoute) + " in parent "
                + formatParent(parent, indexInParent));

        MerkleCopy.copyTreeToLocation(parent.asInternal(), indexInParent, source);

        // Invalidate hashes in path down from root
        new MerkleRouteIterator(getStateEditor().getState(), parent.getRoute())
                .forEachRemaining(Hashable::invalidateHash);
    }
}
