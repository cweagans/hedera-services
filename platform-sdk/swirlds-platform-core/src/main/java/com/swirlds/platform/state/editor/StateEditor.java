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

package com.swirlds.platform.state.editor;

import static com.swirlds.platform.state.editor.StateEditorUtils.formatNodeType;
import static com.swirlds.platform.state.editor.StateEditorUtils.formatRoute;

import com.swirlds.cli.utility.CommandBuilder;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.route.MerkleRouteFactory;
import com.swirlds.common.merkle.route.MerkleRouteUtils;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.DeserializedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import picocli.CommandLine;

/**
 * An interactive SignedState editor.
 */
public class StateEditor {

    private SignedState signedState;
    private MerkleRoute currentWorkingRoute = MerkleRouteFactory.getEmptyRoute();
    private boolean alive = true;

    /**
     * Create a new state editor.
     *
     * @param statePath
     * 		the path where the signed state can be found
     */
    public StateEditor(final Path statePath) throws IOException {
        final DeserializedSignedState deserializedSignedState = SignedStateFileReader.readStateFile(statePath);
        System.out.println("\nLoading state from " + statePath);
        signedState = deserializedSignedState.signedState();
        System.out.println("Hashing state");
        try {
            MerkleCryptoFactory.getInstance()
                    .digestTreeAsync(signedState.getState())
                    .get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException("problem encountered while hashing state", e);
        }
        System.out.println("Hash = " + signedState.getState().getHash());
    }

    /**
     * Set the execution strategy. Required to inject this object into the command object instantiated by piciocli.
     */
    private int executionStrategy(CommandLine.ParseResult parseResult) {
        final Object command =
                parseResult.subcommand().commandSpec().commandLine().getCommand();
        if (command instanceof final StateEditorOperation operation) {
            operation.setStateEditor(this);
        }

        return new CommandLine.RunLast().execute(parseResult);
    }

    /**
     * Build the command line that will parse the user input.
     */
    private CommandLine buildCommandLine() {
        final CommandLine commandLine = CommandBuilder.buildCommandLine(StateEditorRoot.class);
        commandLine.setExecutionStrategy(this::executionStrategy);
        return commandLine;
    }

    /**
     * Start the editor.
     */
    public void start() {
        Scanner reader = new Scanner(System.in);

        CommandLine commandLine = buildCommandLine();
        System.out.println("");
        commandLine.usage(System.out, commandLine.getColorScheme());

        while (alive) {

            MerkleNode target = null;
            while (true) {
                try {
                    target = getState().getNodeAtRoute(currentWorkingRoute);
                    break;
                } catch (final NoSuchElementException e) {
                    // This is possible of the current location gets deleted
                    currentWorkingRoute = currentWorkingRoute.getParent();
                }
            }

            System.out.print("\n" + formatRoute(currentWorkingRoute) + " " + formatNodeType(target) + " $ ");

            final String command = reader.nextLine();
            final String[] args = command.split(" ");

            try {
                final int result = commandLine.execute(args);

                if (result != 0 && commandLine.getParseResult().subcommand() == null) {
                    System.out.println();
                    commandLine.usage(System.out, CommandBuilder.getColorScheme());
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }

            // Rebuild to clear out old arguments.
            commandLine = buildCommandLine();
        }

        reader.close();
    }

    /**
     * Cause the editor to stop at the end of the current operation.
     */
    public void exit() {
        alive = false;
    }

    /**
     * Get the current working route, equivalent to the
     * "current working directory" but relative to the root of the state.
     */
    public MerkleRoute getCurrentWorkingRoute() {
        return currentWorkingRoute;
    }

    /**
     * Set the current working route, equivalent to the
     * "current working directory" but relative to the root of the state.
     */
    public void setCurrentWorkingRoute(final MerkleRoute currentWorkingRoute) {
        getState().getNodeAtRoute(currentWorkingRoute); // throws if invalid
        this.currentWorkingRoute = currentWorkingRoute;
    }

    /**
     * Get the state.
     */
    public State getState() {
        return signedState.getState();
    }

    /**
     * Get the signed state.
     */
    public SignedState getSignedState() {
        return signedState;
    }

    /**
     * Copy the state and return the immutable copy.
     *
     * @return the immutable copy
     */
    public SignedState getSignedStateCopy() {
        final SignedState newSignedState =
                new SignedState(signedState.getState().copy(), signedState.isFreezeState());
        try {
            return signedState;
        } finally {
            signedState = newSignedState;
        }
    }

    /**
     * Parse a relative merkle route string and return the merkle node at that position.
     */
    public MerkleNode getRelativeNode(final String path) {
        return getState().getNodeAtRoute(getRelativeRoute(path));
    }

    /**
     * Parse a route string relative to the current working route.
     *
     * @param path
     * 		a path string
     * @return the route described by that path string
     */
    public MerkleRoute getRelativeRoute(final String path) {
        return MerkleRouteUtils.pathFormatToMerkleRoute(getCurrentWorkingRoute(), path);
    }

    public record ParentInfo(MerkleRoute target, MerkleInternal parent, int indexInParent) {}

    /**
     * Convert a relative merkle route string to information that can be used to add/remove the node at that position.
     * Throws if the parent node is not valid.
     */
    public ParentInfo getParentInfo(final String destinationPath) {
        final MerkleRoute route = getRelativeRoute(destinationPath);
        final MerkleRoute parentPath = route.getParent();
        final int indexInParent = route.getStep(-1);

        final MerkleNode parent = getState().getNodeAtRoute(parentPath);
        if (parent == null) {
            throw new IllegalArgumentException("The node at " + formatRoute(parentPath) + " is null.");
        }
        if (!(parent instanceof MerkleInternal)) {
            throw new IllegalArgumentException("The node at " + formatRoute(parentPath) + " is of type "
                    + parent.getClass().getSimpleName() + " and is not an internal node.");
        }

        return new ParentInfo(route, parent.asInternal(), indexInParent);
    }
}
