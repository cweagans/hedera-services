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

import static com.swirlds.common.merkle.copy.MerkleCopy.copyTreeToLocation;
import static com.swirlds.platform.state.editor.StateEditorUtils.formatNode;

import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRouteIterator;
import picocli.CommandLine;

@CommandLine.Command(name = "swap", mixinStandardHelpOptions = true, description = "Swap two nodes.")
@SubcommandOf(StateEditorRoot.class)
public class StateEditorSwap extends StateEditorOperation {

    private String pathA;
    private String pathB = "";

    @CommandLine.Parameters(index = "0", description = "The first merkle node.")
    private void setPathA(final String pathA) {
        this.pathA = pathA;
    }

    @CommandLine.Parameters(index = "1", arity = "0..1", description = "The second merkle node.")
    private void setPathB(final String pathB) {
        this.pathB = pathB;
    }

    @Override
    public void run() {
        final StateEditor.ParentInfo parentInfoA = getStateEditor().getParentInfo(pathA);
        final StateEditor.ParentInfo parentInfoB = getStateEditor().getParentInfo(pathB);

        final MerkleNode nodeA = getStateEditor().getState().getNodeAtRoute(parentInfoA.target());
        final MerkleNode nodeB = getStateEditor().getState().getNodeAtRoute(parentInfoB.target());

        System.out.println("Swapping " + formatNode(nodeA) + " and " + formatNode(nodeB));

        // Take a reservation on B so it doesn't get prematurely deleted
        if (nodeB != null) {
            nodeB.reserve();
        }

        copyTreeToLocation(parentInfoB.parent(), parentInfoB.indexInParent(), nodeA);
        copyTreeToLocation(parentInfoA.parent(), parentInfoA.indexInParent(), nodeB);

        // Release the artificial reservation
        if (nodeB != null) {
            nodeB.release();
        }

        // Invalidate hashes in path down from root
        new MerkleRouteIterator(
                        getStateEditor().getState(), parentInfoA.parent().getRoute())
                .forEachRemaining(Hashable::invalidateHash);
        new MerkleRouteIterator(
                        getStateEditor().getState(), parentInfoB.parent().getRoute())
                .forEachRemaining(Hashable::invalidateHash);
    }
}
