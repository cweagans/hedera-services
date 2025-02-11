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

package com.swirlds.platform.state.signed;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.SIGNED_STATE;

import com.swirlds.common.Reservable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.utility.AbstractReservable;
import com.swirlds.common.utility.RuntimeObjectRecord;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.platform.Settings;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.MinGenInfo;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedStateHistory.SignedStateAction;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * This is a signed state, in a form that allows those outside the network to verify that it is a legitimate state at a
 * given time.
 * </p>
 *
 * <p>
 * It includes a copy of a SwirldsState at a given moment, and the address book, and the history of the address book,
 * and a set of signatures (with identities of the signers) attesting to it.
 * </p>
 *
 * <p>
 * It can be created at the moment all the famous witnesses become known for a given round. The signatures can be
 * created and collected for every round, or for every Nth round, or for each round whose last famous witness has a
 * consensus time stamp at least T seconds after the last signed state, or by some other criterion.
 * </p>
 *
 * <p>
 * The signed state is also saved to disk, and is given to a new member joining the network, or to an old member
 * rejoining after a long absence.
 * </p>
 */
public class SignedState extends AbstractReservable implements Reservable, SignedStateInfo {

    private static final Logger logger = LogManager.getLogger(SignedState.class);

    /**
     * the signatures collected so far (including from self)
     */
    private SigSet sigSet;

    /**
     * The total stake that has signed this state.
     */
    private long signingStake;

    /**
     * Is this the last state saved before the freeze period
     */
    private boolean freezeState;

    /**
     * True if this state has been deleted. Used to prevent the same state from being deleted more than once.
     */
    private boolean deleted = false;

    /**
     * The root of the merkle state.
     */
    private final State state;

    /**
     * The timestamp of when this object was created.
     */
    private final Instant creationTimestamp = Instant.now();

    /**
     * If true, then this state should eventually be written to disk.
     */
    private boolean stateToSave;

    /**
     * Signed states are deleted on this background thread.
     */
    private SignedStateGarbageCollector signedStateGarbageCollector;

    /**
     * Used to track the lifespan of this signed state.
     */
    private final RuntimeObjectRecord registryRecord;

    /**
     * Information about how this signed state was used.
     */
    private final SignedStateHistory history;

    /**
     * Instantiate a signed state.
     *
     * @param state
     * 		a fast copy of the state resulting from all transactions in consensus order from all
     * 		events with received rounds up through the round this SignedState represents
     * @param freezeState
     * 		specifies whether this state is the last one saved before the freeze
     */
    public SignedState(final State state, final boolean freezeState) {
        this(state);

        if (history != null) {
            history.setRound(state.getPlatformState().getPlatformData().getRound());
        }

        this.freezeState = freezeState;
        sigSet = new SigSet();
    }

    public SignedState(final State state) {
        state.reserve();

        this.state = state;

        if (Settings.getInstance().getState().signedStateSentinelEnabled) {
            history = new SignedStateHistory(OSTime.getInstance());
            history.recordAction(SignedStateHistory.SignedStateAction.CREATION, getReservationCount());
        } else {
            history = null;
        }
        registryRecord = RuntimeObjectRegistry.createRecord(getClass(), history);
    }

    /**
     * Set a garbage collector, used to delete states on a background thread.
     */
    public synchronized void setGarbageCollector(final SignedStateGarbageCollector signedStateGarbageCollector) {
        this.signedStateGarbageCollector = signedStateGarbageCollector;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRound() {
        return state.getPlatformState().getPlatformData().getRound();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SigSet getSigSet() {
        return sigSet;
    }

    /**
     * Attach signatures to this state.
     *
     * @param sigSet the signatures to be attached to this signed state
     */
    public void setSigSet(final SigSet sigSet) {
        signingStake = 0;
        this.sigSet = sigSet;
        for (final long signingNode : sigSet) {
            final Address address = getAddressBook().getAddress(signingNode);
            if (address == null) {
                throw new IllegalStateException(
                        "Signature for node " + signingNode + " found, but that node is not in the address book");
            }
            signingStake += address.getStake();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AddressBook getAddressBook() {
        return Objects.requireNonNull(
                getState().getPlatformState().getAddressBook(),
                "address book stored in this signed state is null, this should never happen");
    }

    /**
     * Get the root of the state. This object should not be held beyond the scope of this SignedState or else there is
     * risk that the state may be deleted unexpectedly.
     *
     * @return the state contained in the signed state
     */
    public State getState() {
        return state;
    }

    /**
     * @return is this the last state saved before the freeze period
     */
    public boolean isFreezeState() {
        return freezeState;
    }

    /**
     * Reserves the SignedState for use. While reserved, this SignedState cannot be deleted, so it is very important to
     * call releaseState() on it when done.
     */
    @Override
    public void reserve() {
        if (history != null) {
            history.recordAction(SignedStateHistory.SignedStateAction.RESERVE, getReservationCount());
        }
        super.reserve();
    }

    /**
     * Releases a reservation previously obtained in reserveState()
     */
    @Override
    public boolean release() {
        if (history != null) {
            history.recordAction(SignedStateAction.RELEASE, getReservationCount());
        }
        return super.release();
    }

    /**
     * Add this state to the queue to be deleted on a background thread.
     */
    @Override
    protected void onDestroy() {
        if (signedStateGarbageCollector == null
                || !signedStateGarbageCollector.executeOnGarbageCollectionThread(this::delete)) {
            logger.warn(
                    SIGNED_STATE.getMarker(),
                    "unable to enqueue state for deletion, " + "will delete state on calling thread {}",
                    Thread.currentThread().getName());
            synchronized (this) {
                delete();
            }
        }
    }

    /**
     * <p>
     * Perform deletion on this signed state.
     * </p>
     *
     * <p>
     * Under normal operation, this method will only be called on the single-threaded background deletion handler.
     * However, if the queue fills up then a different thread may attempt to simultaneously call this method. Because of
     * that, this method must be synchronized.
     * </p>
     */
    private synchronized void delete() {
        final Instant start = Instant.now();

        if (isDestroyed()) {
            if (!deleted) {
                try {
                    deleted = true;

                    if (history != null) {
                        history.recordAction(SignedStateHistory.SignedStateAction.RELEASE, getReservationCount());
                    }
                    registryRecord.release();
                    state.release();

                    if (signedStateGarbageCollector != null) {
                        signedStateGarbageCollector.reportDeleteTime(Duration.between(start, Instant.now()));
                    }
                } catch (final Throwable ex) {
                    logger.error(EXCEPTION.getMarker(), "exception while attempting to delete signed state", ex);
                }
            }
        }
    }

    /**
     * Get the number of reservations.
     */
    @Override
    public synchronized int getReservationCount() {
        return super.getReservationCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SignedState that = (SignedState) o;

        return new EqualsBuilder()
                .append(sigSet, that.sigSet)
                .append(state, that.state)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(sigSet).append(state).toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SS(round: %d, sigs: %d/%s, hash: %s)"
                .formatted(
                        getRound(),
                        signingStake,
                        (getAddressBook() == null ? "?" : getAddressBook().getTotalStake()),
                        state.getHash());
    }

    /**
     * Get the consensus timestamp for this signed state
     *
     * @return the consensus timestamp for this signed state.
     */
    public Instant getConsensusTimestamp() {
        return state.getPlatformState().getPlatformData().getConsensusTimestamp();
    }

    /**
     * The wall clock time when this SignedState object was instantiated.
     */
    public Instant getCreationTimestamp() {
        return creationTimestamp;
    }

    /**
     * Get the root node of the application's state
     *
     * @return the root node of the application's state.
     */
    public SwirldState getSwirldState() {
        return state.getSwirldState();
    }

    /**
     * Get events in the platformState.
     *
     * @return events in the platformState
     */
    public EventImpl[] getEvents() {
        return state.getPlatformState().getPlatformData().getEvents();
    }

    /**
     * Get the hash of the consensus events in this state.
     *
     * @return the hash of the consensus events in this state
     */
    public Hash getHashEventsCons() {
        return state.getPlatformState().getPlatformData().getHashEventsCons();
    }

    /**
     * Get the number of consensus events in this state.
     *
     * @return the number of consensus events in this state
     */
    public long getNumEventsCons() {
        return state.getPlatformState().getPlatformData().getNumEventsCons();
    }

    /**
     * Get information about the minimum generation in this round.
     *
     * @return the minimum generation of famous witnesses per round
     */
    public List<MinGenInfo> getMinGenInfo() {
        return state.getPlatformState().getPlatformData().getMinGenInfo();
    }

    /**
     * The minimum generation of famous witnesses for the round specified. This method only looks at non-ancient rounds
     * contained within this state.
     *
     * @param round the round whose minimum generation will be returned
     * @return the minimum generation for the round specified
     * @throws NoSuchElementException if the generation information for this round is not contained withing this state
     */
    public long getMinGen(final long round) {
        return getState().getPlatformState().getPlatformData().getMinGen(round);
    }

    /**
     * Return the round generation of the oldest round in this state
     *
     * @return the generation of the oldest round
     */
    public long getMinRoundGeneration() {
        return getState().getPlatformState().getPlatformData().getMinRoundGeneration();
    }

    /**
     * Get the timestamp of the last transaction added to this state.
     *
     * @return the timestamp of the last transaction added to this state
     */
    public Instant getLastTransactionTimestamp() {
        return state.getPlatformState().getPlatformData().getLastTransactionTimestamp();
    }

    /**
     * Check if this is a state that needs to be eventually written to disk.
     *
     * @return true if this state eventually needs to be written to disk
     */
    public boolean isStateToSave() {
        return stateToSave;
    }

    /**
     * Set if this state eventually needs to be written to disk.
     *
     * @param stateToSave true if this state eventually needs to be written to disk
     */
    public void setStateToSave(final boolean stateToSave) {
        this.stateToSave = stateToSave;
    }

    /**
     * Get the total signing stake collected so far.
     *
     * @return total stake of members whose signatures have been collected
     */
    public long getSigningStake() {
        return signingStake;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isComplete() {
        return Utilities.isMajority(signingStake, getAddressBook().getTotalStake());
    }

    /**
     * Throw an exception if this state has not been completely signed. This method does not validate signatures, call
     * {@link #pruneInvalidSignatures()} to guarantee that only valid signatures are considered.
     *
     * @throws SignedStateInvalidException if this state lacks sufficient signatures to be considered complete
     */
    public void throwIfIncomplete() {
        if (!isComplete()) {
            throw new SignedStateInvalidException(
                    "Signed state lacks sufficient valid signatures. This state has " + sigSet.size()
                            + " valid signatures representing " + signingStake + "/"
                            + getAddressBook().getTotalStake() + " stake");
        }
    }

    /**
     * Add a signature to the sigset if the signature is valid.
     *
     * @param nodeId    the ID of the signing node
     * @param signature the signature to add
     * @return true if the signed state is now complete as a result of the signature being added, false if the signed
     * state is either not complete or was previously complete prior to this signature
     */
    public boolean addSignature(final long nodeId, final Signature signature) {
        return addSignature(getAddressBook(), nodeId, signature);
    }

    /**
     * Check if a signature is valid.  If a node has no stake, we consider the signature to be invalid.
     *
     * @param address   the address of the signer, or null if there is no signing address
     * @param signature the signature to check
     * @return true if the signature is valid, false otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isSignatureValid(final Address address, final Signature signature) {
        if (address == null) {
            // Signing node is not in the address book.
            return false;
        }

        if (address.getStake() == 0) {
            // Signing node has no stake.
            return false;
        }

        return signature.verifySignature(state.getHash().getValue(), address.getSigPublicKey());
    }

    /**
     * Add a signature to the sigset if the signature is valid.
     *
     * @param addressBook use this address book to determine if the signature is valid or not
     * @param nodeId      the ID of the signing node
     * @param signature   the signature to add
     * @return true if the signed state is now complete as a result of the signature being added, false if the signed
     * state is either not complete or was previously complete prior to this signature
     */
    private boolean addSignature(final AddressBook addressBook, final long nodeId, final Signature signature) {
        throwArgNull(addressBook, "addressBook");
        throwArgNull(signature, "signature");

        if (isComplete()) {
            // No need to add more signatures
            return false;
        }

        final Address address = addressBook.getAddress(nodeId);
        if (!isSignatureValid(address, signature)) {
            return false;
        }

        if (sigSet.hasSignature(address.getId())) {
            // We already have this signature.
            return false;
        }

        sigSet.addSignature(nodeId, signature);
        signingStake += address.getStake();

        return isComplete();
    }

    /**
     * Remove all invalid signatures from a signed state. Uses the address book in the state when judging the validity
     * of signatures.
     */
    public void pruneInvalidSignatures() {
        pruneInvalidSignatures(getAddressBook());
    }

    /**
     * Remove all invalid signatures from a signed state.
     *
     * @param trustedAddressBook use this address book to determine signature validity instead of the one inside the
     *                           signed state. Useful if validating signed states from untrusted sources.
     */
    public void pruneInvalidSignatures(final AddressBook trustedAddressBook) {
        final List<Long> signaturesToRemove = new ArrayList<>();
        for (final long nodeId : sigSet) {
            final Address address = trustedAddressBook.getAddress(nodeId);
            if (!isSignatureValid(address, sigSet.getSignature(nodeId))) {
                signaturesToRemove.add(nodeId);
            }
        }

        for (final long nodeId : signaturesToRemove) {
            sigSet.removeSignature(nodeId);
        }

        // Recalculate signing stake. We should do this even if we don't remove signatures.
        signingStake = 0;
        for (final long nodeId : sigSet) {
            signingStake += trustedAddressBook.getAddress(nodeId).getStake();
        }
    }
}
