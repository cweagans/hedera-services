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

package com.swirlds.platform.test.state;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomHash;
import static com.swirlds.platform.Utilities.isMajority;
import static com.swirlds.platform.Utilities.isSuperMajority;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.platform.dispatch.triggers.flow.StateHashValidityTrigger;
import com.swirlds.platform.state.iss.internal.HashValidityStatus;
import com.swirlds.platform.state.iss.internal.RoundHashValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("RoundHashValidator Tests")
class RoundHashValidatorTests {

    static Stream<Arguments> args() {
        return Stream.of(
                Arguments.of(HashValidityStatus.VALID),
                Arguments.of(HashValidityStatus.SELF_ISS),
                Arguments.of(HashValidityStatus.CATASTROPHIC_ISS));
    }

    private static final StateHashValidityTrigger NO_OP_DISAGREEMENT_DISPATCHER = (a, b, c, d) -> {};

    record NodeHashInfo(long nodeId, Hash nodeStateHash, long round) {}

    record HashGenerationData(List<NodeHashInfo> nodeList, Hash consensusHash) {}

    /**
     * Based on the desired network status, generate hashes for all nodes.
     *
     * @param random
     * 		a source of randomness
     * @param addressBook
     * 		the address book for the round
     * @param desiredValidityStatus
     * 		the desired validity status
     * @return a list of node IDs in the order they should be added to the hash validator
     */
    static HashGenerationData generateNodeHashes(
            final Random random,
            final AddressBook addressBook,
            final HashValidityStatus desiredValidityStatus,
            long round) {
        if (desiredValidityStatus == HashValidityStatus.VALID || desiredValidityStatus == HashValidityStatus.SELF_ISS) {
            return generateRegularNodeHashes(random, addressBook, round);
        } else if (desiredValidityStatus == HashValidityStatus.CATASTROPHIC_ISS) {
            return generateCatastrophicNodeHashes(random, addressBook, round);
        } else {
            throw new IllegalArgumentException("Unsupported case " + desiredValidityStatus);
        }
    }

    /**
     * Generate node hashes without there being a catastrophic ISS.
     */
    static HashGenerationData generateRegularNodeHashes(
            final Random random, final AddressBook addressBook, long round) {

        // Greater than 1/2 must have the same hash. But all other nodes are free to take whatever other hash
        // they want. Choose that fraction randomly.

        final List<NodeHashInfo> nodes = new LinkedList<>();

        final List<Long> randomNodeOrder = new LinkedList<>();
        addressBook.iterator().forEachRemaining(address -> randomNodeOrder.add(address.getId()));
        Collections.shuffle(randomNodeOrder, random);

        final long totalStake = addressBook.getTotalStake();

        // This is the hash we want to be chosen for the consensus hash.
        final Hash consensusHash = randomHash(random);
        final List<Long> correctHashNodes = new LinkedList<>();
        long correctHashStake = 0;

        // A large group of nodes may decide to use this hash. But it won't become the consensus hash.
        final Hash otherHash = randomHash(random);
        long otherHashStake = 0;
        final List<Long> otherHashNodes = new LinkedList<>();

        // All remaining nodes will choose a hash randomly.
        final List<Long> randomHashNodes = new LinkedList<>();

        // Assign each node to one of the hashing strategies described above.
        for (final long nodeId : randomNodeOrder) {
            final long stake = addressBook.getAddress(nodeId).getStake();

            if (!isMajority(correctHashStake, totalStake)) {
                correctHashNodes.add(nodeId);
                correctHashStake += stake;
            } else {
                if (random.nextBoolean()) {
                    otherHashNodes.add(nodeId);
                } else {
                    randomHashNodes.add(nodeId);
                }
            }
        }

        correctHashStake = 0;

        // For the sake of sanity, don't let this loop go on forever
        int allowedIterations = 1_000_000;

        // Now, decide what order the hashes should be processed. Make sure that the
        // consensus hash is the first to reach a strong minority.
        while (nodes.size() < addressBook.getSize()) {
            final double choice = random.nextDouble();

            allowedIterations--;
            assertTrue(allowedIterations > 0, "loop is not terminating");

            if (choice < 1.0 / 3) {
                if (!correctHashNodes.isEmpty()) {
                    final long nodeId = correctHashNodes.remove(0);
                    final long stake = addressBook.getAddress(nodeId).getStake();
                    nodes.add(new NodeHashInfo(nodeId, consensusHash, round));
                    correctHashStake += stake;
                }
            } else if (choice < 2.0 / 3) {
                if (!otherHashNodes.isEmpty()) {
                    final long nodeId = otherHashNodes.get(0);
                    final long stake = addressBook.getAddress(nodeId).getStake();

                    if (isMajority(otherHashStake + stake, totalStake)) {
                        // We don't want to allow the other hash to accumulate >1/2
                        continue;
                    }

                    otherHashNodes.remove(0);

                    nodes.add(new NodeHashInfo(nodeId, otherHash, round));
                    otherHashStake += addressBook.getAddress(nodeId).getStake();
                }
            } else {
                // The random hashes will never reach a majority, so they can go in whenever
                if (!randomHashNodes.isEmpty()) {
                    final long nodeId = randomHashNodes.remove(0);
                    nodes.add(new NodeHashInfo(nodeId, randomHash(random), round));
                }
            }
        }

        return new HashGenerationData(nodes, consensusHash);
    }

    /**
     * Generate node hashes that result in a catastrophic ISS.
     */
    static HashGenerationData generateCatastrophicNodeHashes(
            final Random random, final AddressBook addressBook, long round) {

        // There should exist no group of nodes with the same hash that >1/2

        final List<NodeHashInfo> nodes = new ArrayList<>();

        final long totalStake = addressBook.getTotalStake();

        final List<Long> randomNodeOrder = new LinkedList<>();
        addressBook.iterator().forEachRemaining(address -> randomNodeOrder.add(address.getId()));
        Collections.shuffle(randomNodeOrder, random);

        // A large group of nodes may decide to use this hash. But it won't become the consensus hash.
        final Hash otherHash = randomHash(random);
        long otherHashStake = 0;

        for (final long nodeId : randomNodeOrder) {
            final long stake = addressBook.getAddress(nodeId).getStake();

            final double choice = random.nextDouble();
            if (choice < 1.0 / 3 && !isMajority(otherHashStake + stake, totalStake)) {
                nodes.add(new NodeHashInfo(nodeId, otherHash, round));
                otherHashStake += stake;
            } else {
                nodes.add(new NodeHashInfo(nodeId, randomHash(random), round));
            }
        }

        return new HashGenerationData(nodes, null);
    }

    /**
     * Choose a node to be the "self" node
     */
    private static NodeHashInfo chooseSelfNode(
            final Random random,
            final HashGenerationData hashGenerationData,
            final HashValidityStatus desiredHashValidityStatus) {

        if (desiredHashValidityStatus == HashValidityStatus.CATASTROPHIC_ISS) {
            // It doesn't matter which node we choose
            return hashGenerationData.nodeList.get(random.nextInt(hashGenerationData.nodeList.size()));
        } else if (desiredHashValidityStatus == HashValidityStatus.SELF_ISS) {
            final List<NodeHashInfo> possibleChoices = new ArrayList<>();
            for (final NodeHashInfo node : hashGenerationData.nodeList) {
                if (!node.nodeStateHash.equals(hashGenerationData.consensusHash)) {
                    possibleChoices.add(node);
                }
            }
            return possibleChoices.get(random.nextInt(possibleChoices.size()));

        } else if (desiredHashValidityStatus == HashValidityStatus.VALID) {
            final List<NodeHashInfo> possibleChoices = new ArrayList<>();
            for (final NodeHashInfo node : hashGenerationData.nodeList) {
                if (node.nodeStateHash.equals(hashGenerationData.consensusHash)) {
                    possibleChoices.add(node);
                }
            }
            return possibleChoices.get(random.nextInt(possibleChoices.size()));
        } else {
            throw new IllegalArgumentException();
        }
    }

    @ParameterizedTest
    @MethodSource("args")
    @DisplayName("Self Signature Last Test")
    void selfSignatureLastTest(final HashValidityStatus expectedStatus) {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(Math.max(10, random.nextInt(1000)))
                .setSequentialIds(false)
                .setAverageStake(100)
                .setStakeStandardDeviation(50)
                .build();

        final HashGenerationData hashGenerationData = generateNodeHashes(random, addressBook, expectedStatus, 0);
        final NodeHashInfo thisNode = chooseSelfNode(random, hashGenerationData, expectedStatus);

        final long round = random.nextInt(1000);
        final RoundHashValidator validator =
                new RoundHashValidator(NO_OP_DISAGREEMENT_DISPATCHER, round, addressBook.getTotalStake());

        boolean decided = false;

        for (final NodeHashInfo nodeHashInfo : hashGenerationData.nodeList) {
            final long nodeId = nodeHashInfo.nodeId;
            final long stake = addressBook.getAddress(nodeId).getStake();
            final Hash hash = nodeHashInfo.nodeStateHash;

            final boolean operationCausedDecision = validator.reportHashFromNetwork(nodeId, stake, hash);

            if (expectedStatus != HashValidityStatus.CATASTROPHIC_ISS) {
                assertFalse(operationCausedDecision, "should not be decided until self hash is added");
                assertEquals(HashValidityStatus.UNDECIDED, validator.getStatus(), "should not yet be decided");
            } else if (operationCausedDecision) {
                assertFalse(decided, "should only be decided once");
                decided = true;
            }
        }

        if (expectedStatus != HashValidityStatus.CATASTROPHIC_ISS) {
            assertTrue(validator.reportSelfHash(thisNode.nodeStateHash), "validator should now be decided");
        } else {
            assertFalse(validator.reportSelfHash(thisNode.nodeStateHash), "validator should already be decided");
        }
        assertFalse(validator.outOfTime(), "timing out should have no effect here");
        assertEquals(expectedStatus, validator.getStatus(), "unexpected status");
    }

    @ParameterizedTest
    @MethodSource("args")
    @DisplayName("Self Signature First Test")
    void selfSignatureFirstTest(final HashValidityStatus expectedStatus) {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(Math.max(10, random.nextInt(1000)))
                .setSequentialIds(false)
                .setAverageStake(100)
                .setStakeStandardDeviation(50)
                .build();

        final HashGenerationData hashGenerationData = generateNodeHashes(random, addressBook, expectedStatus, 0);
        final NodeHashInfo thisNode = chooseSelfNode(random, hashGenerationData, expectedStatus);

        final long round = random.nextInt(1000);
        final RoundHashValidator validator =
                new RoundHashValidator(NO_OP_DISAGREEMENT_DISPATCHER, round, addressBook.getTotalStake());

        boolean decided = false;

        assertFalse(
                validator.reportSelfHash(thisNode.nodeStateHash),
                "we should need to gather more data before becoming decided");

        for (final NodeHashInfo nodeHashInfo : hashGenerationData.nodeList) {
            final long nodeId = nodeHashInfo.nodeId;
            final long stake = addressBook.getAddress(nodeId).getStake();
            final Hash hash = nodeHashInfo.nodeStateHash;

            final boolean operationCausedDecision = validator.reportHashFromNetwork(nodeId, stake, hash);

            if (operationCausedDecision) {
                assertFalse(decided, "should only be decided once");
                decided = true;
            }
        }

        assertFalse(validator.outOfTime(), "timing out should have no effect here");

        assertTrue(decided, "should have been decided");
        assertEquals(expectedStatus, validator.getStatus(), "unexpected status");
    }

    @ParameterizedTest
    @MethodSource("args")
    @DisplayName("Self Signature In Middle Test")
    void selfSignatureInMiddleTest(final HashValidityStatus expectedStatus) {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(Math.max(10, random.nextInt(1000)))
                .setSequentialIds(false)
                .setAverageStake(100)
                .setStakeStandardDeviation(50)
                .build();

        final HashGenerationData hashGenerationData = generateNodeHashes(random, addressBook, expectedStatus, 0);
        final NodeHashInfo thisNode = chooseSelfNode(random, hashGenerationData, expectedStatus);

        final long round = random.nextInt(1000);
        final RoundHashValidator validator =
                new RoundHashValidator(NO_OP_DISAGREEMENT_DISPATCHER, round, addressBook.getTotalStake());

        boolean decided = false;

        final int addSelfHashIndex = random.nextInt(addressBook.getSize() - 1);
        int index = 0;

        for (final NodeHashInfo nodeHashInfo : hashGenerationData.nodeList) {
            final long nodeId = nodeHashInfo.nodeId;
            final long stake = addressBook.getAddress(nodeId).getStake();
            final Hash hash = nodeHashInfo.nodeStateHash;

            if (index == addSelfHashIndex) {
                final boolean operationCausedDecision = validator.reportSelfHash(thisNode.nodeStateHash);
                if (operationCausedDecision) {
                    assertFalse(decided, "should only be decided once");
                    decided = true;
                }
            }
            index++;

            final boolean operationCausedDecision = validator.reportHashFromNetwork(nodeId, stake, hash);
            if (operationCausedDecision) {
                assertFalse(decided, "should only be decided once");
                decided = true;
            }
        }

        assertFalse(validator.outOfTime(), "timing out should have no effect here");

        assertTrue(decided, "should have been decided");
        assertEquals(expectedStatus, validator.getStatus(), "unexpected status");
    }

    @Test
    @DisplayName("Timeout Self Hash Test")
    void timeoutSelfHashTest() {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(Math.max(10, random.nextInt(1000)))
                .setSequentialIds(false)
                .setAverageStake(100)
                .setStakeStandardDeviation(50)
                .build();

        final HashGenerationData hashGenerationData =
                generateNodeHashes(random, addressBook, HashValidityStatus.VALID, 0);

        final long round = random.nextInt(1000);
        final RoundHashValidator validator =
                new RoundHashValidator(NO_OP_DISAGREEMENT_DISPATCHER, round, addressBook.getTotalStake());

        for (final NodeHashInfo nodeHashInfo : hashGenerationData.nodeList) {
            final long nodeId = nodeHashInfo.nodeId;
            final long stake = addressBook.getAddress(nodeId).getStake();
            final Hash hash = nodeHashInfo.nodeStateHash;

            assertFalse(validator.reportHashFromNetwork(nodeId, stake, hash), "insufficient data to make decision");
            assertEquals(HashValidityStatus.UNDECIDED, validator.getStatus(), "should not be decided");
        }

        assertTrue(
                validator.outOfTime(), "since we have not decided, running out of time will make the decision for us");

        assertEquals(HashValidityStatus.LACK_OF_DATA, validator.getStatus(), "we should lack data");
    }

    @Test
    @DisplayName("Timeout Self Hash And Signatures Test")
    void timeoutSelfHashAndSignaturesTest() {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(Math.max(10, random.nextInt(1000)))
                .setSequentialIds(false)
                .setAverageStake(100)
                .setStakeStandardDeviation(50)
                .build();
        final long totalStake = addressBook.getTotalStake();

        final HashGenerationData hashGenerationData =
                generateNodeHashes(random, addressBook, HashValidityStatus.VALID, 0);

        final long round = random.nextInt(1000);
        final RoundHashValidator validator =
                new RoundHashValidator(NO_OP_DISAGREEMENT_DISPATCHER, round, addressBook.getTotalStake());

        long addedStake = 0;

        for (final NodeHashInfo nodeHashInfo : hashGenerationData.nodeList) {
            final long nodeId = nodeHashInfo.nodeId;
            final long stake = addressBook.getAddress(nodeId).getStake();
            final Hash hash = nodeHashInfo.nodeStateHash;

            if (isMajority(addedStake + stake, totalStake)) {
                // Don't add enough hash data to reach a decision
                break;
            }

            assertFalse(validator.reportHashFromNetwork(nodeId, stake, hash), "insufficient data to make decision");
            assertEquals(HashValidityStatus.UNDECIDED, validator.getStatus(), "should not be decided");
        }

        assertTrue(
                validator.outOfTime(), "since we have not decided, running out of time will make the decision for us");

        assertEquals(HashValidityStatus.LACK_OF_DATA, validator.getStatus(), "we should lack data");
    }

    @Test
    @DisplayName("Timeout Self Hash And Signatures Test")
    void timeoutSignaturesTest() {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(Math.max(10, random.nextInt(1000)))
                .setSequentialIds(false)
                .setAverageStake(100)
                .setStakeStandardDeviation(50)
                .build();
        final long totalStake = addressBook.getTotalStake();

        final HashGenerationData hashGenerationData =
                generateNodeHashes(random, addressBook, HashValidityStatus.VALID, 0);
        final NodeHashInfo thisNode = chooseSelfNode(random, hashGenerationData, HashValidityStatus.VALID);

        final long round = random.nextInt(1000);
        final RoundHashValidator validator =
                new RoundHashValidator(NO_OP_DISAGREEMENT_DISPATCHER, round, addressBook.getTotalStake());

        assertFalse(validator.reportSelfHash(thisNode.nodeStateHash), "should not allow a decision");

        long addedStake = 0;

        for (final NodeHashInfo nodeHashInfo : hashGenerationData.nodeList) {
            final long nodeId = nodeHashInfo.nodeId;
            final long stake = addressBook.getAddress(nodeId).getStake();
            final Hash hash = nodeHashInfo.nodeStateHash;

            if (isMajority(addedStake + stake, totalStake)) {
                // Don't add enough hash data to reach a decision
                break;
            }
            addedStake += stake;

            assertFalse(validator.reportHashFromNetwork(nodeId, stake, hash), "insufficient data to make decision");
            assertEquals(HashValidityStatus.UNDECIDED, validator.getStatus(), "should not be decided");
        }

        assertTrue(
                validator.outOfTime(), "since we have not decided, running out of time will make the decision for us");

        assertEquals(HashValidityStatus.LACK_OF_DATA, validator.getStatus(), "we should lack data");
    }

    @Test
    @DisplayName("Timeout With Super Majority Test")
    void timeoutWithSuperMajorityTest() {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(Math.max(10, random.nextInt(1000)))
                .setSequentialIds(false)
                .setAverageStake(100)
                .setStakeStandardDeviation(50)
                .build();
        final long totalStake = addressBook.getTotalStake();

        final HashGenerationData hashGenerationData =
                generateNodeHashes(random, addressBook, HashValidityStatus.CATASTROPHIC_ISS, 0);
        final NodeHashInfo thisNode = chooseSelfNode(random, hashGenerationData, HashValidityStatus.CATASTROPHIC_ISS);

        final long round = random.nextInt(1000);
        final RoundHashValidator validator =
                new RoundHashValidator(NO_OP_DISAGREEMENT_DISPATCHER, round, addressBook.getTotalStake());

        assertFalse(validator.reportSelfHash(thisNode.nodeStateHash), "should not allow a decision");

        long addedStake = 0;

        for (final NodeHashInfo nodeHashInfo : hashGenerationData.nodeList) {
            final long nodeId = nodeHashInfo.nodeId;
            final long stake = addressBook.getAddress(nodeId).getStake();
            final Hash hash = nodeHashInfo.nodeStateHash;

            boolean decided = validator.reportHashFromNetwork(nodeId, stake, hash);

            if (decided) {
                // There is a very low probability that the chosen data set will
                // have a catastrophic ISS that is discoverable at this point
                // in time (~1%). That's not the scenario we are trying
                // to test. But we shouldn't fail if the data choice was unlucky.
                assertEquals(HashValidityStatus.CATASTROPHIC_ISS, validator.getStatus());
                assertTrue(isMajority(addedStake + stake, totalStake));
                return;
            }

            assertEquals(HashValidityStatus.UNDECIDED, validator.getStatus(), "should not be decided");

            addedStake += stake;
            if (isSuperMajority(addedStake, totalStake)) {
                // quit once we add a super majority
                break;
            }
        }

        assertTrue(
                validator.outOfTime(), "since we have not decided, running out of time will make the decision for us");

        assertEquals(
                HashValidityStatus.CATASTROPHIC_LACK_OF_DATA,
                validator.getStatus(),
                "gathering >= 2/3 without reaching a decision should lead to catastrophic lack of data");
    }
}
