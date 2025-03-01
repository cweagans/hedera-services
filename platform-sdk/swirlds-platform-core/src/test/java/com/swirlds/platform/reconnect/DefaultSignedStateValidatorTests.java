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

package com.swirlds.platform.reconnect;

import static com.swirlds.common.test.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateInvalidException;
import com.swirlds.platform.state.signed.SignedStateValidationData;
import com.swirlds.test.framework.TestQualifierTags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that the {@link DefaultSignedStateValidator} uses stake correctly to determine the validity of the signed state.
 */
class DefaultSignedStateValidatorTests {

    private static final int NUM_NODES_IN_STATIC_TESTS = 7;

    /** The maximum number of nodes in the network (inclusive) in randomized tests. */
    private static final int MAX_NODES_IN_RANDOMIZED_TESTS = 20;

    /** The maximum amount of stake to allocate to a single node (inclusive) in randomized tests. */
    private static final int MAX_STAKE_PER_NODE = 100;

    private static final int ROUND = 0;

    private AddressBook addressBook;

    private DefaultSignedStateValidator validator;

    /**
     * Test params to test specific scenarios of node signatures and stake values.
     *
     * @return stream of arguments to test specific scenarios
     */
    private static Stream<Arguments> staticNodeParams() {
        final List<Arguments> arguments = new ArrayList<>();

        // All state signatures are valid and make up a majority
        arguments.add(allNodesValidSignatures());

        // All state signatures are valid but do not make up a majority
        arguments.add(allValidSigsNoMajority());

        // 1/2 stake of valid signatures, 1/2 of stake invalid signatures
        arguments.add(someNodeValidSigsMajority());

        // less than 1/2 stake of valid signatures, more than 1/2 of stake invalid signatures
        arguments.add(someNodeValidSigsNoMajority());

        return arguments.stream();
    }

    /**
     * Test params to test randomized scenarios. Randomized variables:
     * </p>
     * <ul>
     *     <li>network size</li>
     *     <li>stake per node</li>
     *     <li>which nodes sign the state</li>
     *     <li>if a nodes signs the state, do they use a valid or invalid signature</li>
     * </ul>
     *
     * @return stream of arguments to test randomized scenarios
     */
    private static Stream<Arguments> randomizedNodeParams() {
        final List<Arguments> arguments = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            final Long seed = new Random().nextLong();
            final RandomGenerator r = RandomUtils.initRandom(seed);
            final List<Node> nodes = initRandomizedNodes(r);
            final List<Node> signingNodes = getRandomizedSigningNodes(r, nodes);
            final long validSigningStake = getValidSignatureStake(signingNodes);
            final long totalState = getTotalStake(nodes);
            final String desc = String.format(
                    "\nseed: %sL:, valid signing stake: %s, total stake: %s\n", seed, validSigningStake, totalState);
            arguments.add(Arguments.of(desc, nodes, signingNodes));
        }

        return arguments.stream();
    }

    private static List<Node> getRandomizedSigningNodes(final RandomGenerator r, final List<Node> nodes) {
        final List<Node> signingNodes = new LinkedList<>();
        for (final Node node : nodes) {
            if (r.nextBoolean()) {
                signingNodes.add(node);
            }
        }
        return signingNodes;
    }

    private static List<Node> initRandomizedNodes(final RandomGenerator r) {
        final int numNodes = r.nextInt(1, MAX_NODES_IN_RANDOMIZED_TESTS);
        final List<Node> nodes = new LinkedList<>();
        for (int i = 0; i < numNodes; i++) {
            // Allow zero-stake
            final int stake = r.nextInt(MAX_STAKE_PER_NODE);
            final boolean hasValidSig = r.nextBoolean();
            nodes.add(new Node(i, stake, hasValidSig));
        }
        return nodes;
    }

    /**
     * @return Arguments to test that all signatures on the state are valid but do not constitute a majority.
     */
    private static Arguments allValidSigsNoMajority() {
        final List<Node> allValidSigNodes = initNodes();
        final Long seed = new Random().nextLong();
        return Arguments.of(
                formatSeedDesc(seed),
                allValidSigNodes,
                List.of(
                        allValidSigNodes.get(0),
                        allValidSigNodes.get(1),
                        allValidSigNodes.get(2),
                        allValidSigNodes.get(3)));
    }

    /**
     * @return Arguments to test when all nodes sign a state with a valid signature.
     */
    private static Arguments allNodesValidSignatures() {
        final List<Node> allValidSigNodes = initNodes();
        final Long seed = new Random().nextLong();
        return Arguments.of(formatSeedDesc(seed), allValidSigNodes, allValidSigNodes);
    }

    private static String formatSeedDesc(final Long seed) {
        return "\nseed: " + seed + "L";
    }

    /**
     * @return Arguments to test when all nodes sign a state, some with invalid signatures, but the valid signatures
     * 		constitute a majority.
     */
    private static Arguments someNodeValidSigsMajority() {
        // >1/2 stake of valid signatures, <1/2 of stake invalid signatures
        final List<Node> majorityValidSigs = initNodes(List.of(true, false, true, false, false, false, true));
        final Long seed = new Random().nextLong();
        return Arguments.of(formatSeedDesc(seed), majorityValidSigs, majorityValidSigs);
    }

    /**
     * @return Arguments to test when all nodes sign a state, some with invalid signatures, and the valid signatures
     * 		do not constitute a majority.
     */
    private static Arguments someNodeValidSigsNoMajority() {
        final List<Node> notMajorityValidSigs = initNodes(List.of(true, true, true, true, false, false, false));
        final Long seed = new Random().nextLong();
        return Arguments.of(formatSeedDesc(seed), notMajorityValidSigs, notMajorityValidSigs);
    }

    /**
     * Creates a list of nodes, some of which may sign a state with an invalid signature
     *
     * @param isValidSigList
     * 		a list of booleans indicating if the node at that position will sign the state with a valid signature
     * @return a list of nodes
     */
    private static List<Node> initNodes(final List<Boolean> isValidSigList) {
        if (isValidSigList.size() != NUM_NODES_IN_STATIC_TESTS) {
            throw new IllegalArgumentException(String.format(
                    "Incorrect isValidSigList size. Expected %s but got %s",
                    NUM_NODES_IN_STATIC_TESTS, isValidSigList.size()));
        }

        final List<Node> nodes = new ArrayList<>(NUM_NODES_IN_STATIC_TESTS);
        nodes.add(new Node(0L, 5L, isValidSigList.get(0)));
        nodes.add(new Node(1L, 5L, isValidSigList.get(1)));
        nodes.add(new Node(2L, 8L, isValidSigList.get(2)));
        nodes.add(new Node(3L, 15L, isValidSigList.get(3)));
        nodes.add(new Node(4L, 17L, isValidSigList.get(4)));
        nodes.add(new Node(5L, 10L, isValidSigList.get(5)));
        nodes.add(new Node(6L, 30L, isValidSigList.get(6)));
        return nodes;
    }

    /**
     * @return a list of nodes whose signatures are all valid
     */
    private static List<Node> initNodes() {
        return initNodes(IntStream.range(0, NUM_NODES_IN_STATIC_TESTS)
                .mapToObj(i -> Boolean.TRUE)
                .collect(Collectors.toList()));
    }

    @ParameterizedTest
    @MethodSource({"staticNodeParams", "randomizedNodeParams"})
    @DisplayName("Signed State Validation")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void testSignedStateValidationRandom(final String desc, final List<Node> nodes, final List<Node> signingNodes) {
        addressBook = new RandomAddressBookGenerator()
                .setSize(nodes.size())
                .setCustomStakeGenerator(id -> nodes.get((int) id).stake)
                .setSequentialIds(true)
                .build();

        validator = new DefaultSignedStateValidator();

        final SignedState signedState = stateSignedByNodes(signingNodes);
        final SignedStateValidationData originalData = new SignedStateValidationData(
                signedState.getState().getPlatformState().getPlatformData(), addressBook);

        final boolean shouldSucceed = stateHasEnoughStake(nodes, signingNodes);
        if (shouldSucceed) {
            assertDoesNotThrow(
                    () -> validator.validate(signedState, addressBook, originalData),
                    "State signed with a majority of stake (%s out of %s) should pass validation."
                            .formatted(getValidSignatureStake(signingNodes), getTotalStake(nodes)));
        } else {
            assertThrows(
                    SignedStateInvalidException.class,
                    () -> validator.validate(signedState, addressBook, originalData),
                    "State not signed with a majority of stake (%s out of %s) should NOT pass validation."
                            .formatted(getValidSignatureStake(signingNodes), getTotalStake(nodes)));
        }
    }

    /**
     * Determines if the nodes in {@code signingNodes} with valid signatures have enough stake to make up a strong
     * minority of the total stake.
     *
     * @param nodes
     * 		all the nodes in the network, used to calculate the total stake
     * @param signingNodes
     * 		all the nodes that signed the state
     * @return true if the state has a majority of stake from valid signatures
     */
    private boolean stateHasEnoughStake(final List<Node> nodes, final List<Node> signingNodes) {
        final long totalStake = getTotalStake(nodes);
        final long signingStake = getValidSignatureStake(signingNodes);

        return Utilities.isMajority(signingStake, totalStake);
    }

    private static long getTotalStake(final List<Node> nodes) {
        long totalStake = 0;
        for (final Node node : nodes) {
            totalStake += node.stake;
        }
        return totalStake;
    }

    private static long getValidSignatureStake(final List<Node> signingNodes) {
        long signingStake = 0;
        for (final Node signingNode : signingNodes) {
            signingStake += signingNode.validSignature ? signingNode.stake : 0;
        }
        return signingStake;
    }

    /**
     * Create a {@link SignedState} signed by the nodes whose ids are supplied by {@code signingNodeIds}.
     *
     * @param signingNodes
     * 		the node ids signing the state
     * @return the signed state
     */
    private SignedState stateSignedByNodes(final List<Node> signingNodes) {

        final Hash stateHash = randomHash();

        return new RandomSignedStateGenerator()
                .setRound(ROUND)
                .setAddressBook(addressBook)
                .setStateHash(stateHash)
                .setSignatures(nodeSigs(signingNodes, stateHash))
                .build();
    }

    /**
     * @return a list of the nodes ids in the supplied nodes
     */
    private Map<Long, Signature> nodeSigs(final List<Node> nodes, final Hash stateHash) {
        final Map<Long, Signature> signatures = new HashMap<>();
        for (final Node node : nodes) {

            final Signature signature = mock(Signature.class);
            doAnswer(invocation -> {
                        if (!node.validSignature) {
                            // This signature is always invalid
                            return false;
                        }

                        final byte[] bytes = invocation.getArgument(0);
                        final Hash hash = new Hash(bytes, stateHash.getDigestType());

                        return hash.equals(stateHash);
                    })
                    .when(signature)
                    .verifySignature(any(), any());

            signatures.put(node.id, signature);
        }

        return signatures;
    }

    /**
     * A record representing a simple node that holds its id, amount of stake, and if is signs states with a valid
     * signature.
     */
    private record Node(long id, long stake, boolean validSignature) {

        @Override
        public String toString() {
            return String.format("NodeId: %s,\tStake: %s,\tValidSig: %s", id, stake, validSignature);
        }
    }
}
