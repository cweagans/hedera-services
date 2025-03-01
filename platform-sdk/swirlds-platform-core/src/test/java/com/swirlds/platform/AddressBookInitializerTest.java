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

package com.swirlds.platform;

import static com.swirlds.platform.AddressBookInitializer.CONFIG_ADDRESS_BOOK_HEADER;
import static com.swirlds.platform.AddressBookInitializer.CONFIG_ADDRESS_BOOK_USED;
import static com.swirlds.platform.AddressBookInitializer.STATE_ADDRESS_BOOK_HEADER;
import static com.swirlds.platform.AddressBookInitializer.STATE_ADDRESS_BOOK_NULL;
import static com.swirlds.platform.AddressBookInitializer.STATE_ADDRESS_BOOK_USED;
import static com.swirlds.platform.AddressBookInitializer.USED_ADDRESS_BOOK_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.OngoingStubbing;

class AddressBookInitializerTest {

    @TempDir
    Path testDirectory;

    @Test
    @DisplayName("Force the use of the config address book, null signed state")
    void forceUseOfConfigAddressBookNullSignedState() throws IOException {
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook();
        final AddressBookInitializer initializer = new AddressBookInitializer(
                getMockSoftwareVersion(1),
                getMockSignedState(0),
                getMockSwirldStateSupplier(1),
                configAddressBook,
                getAddressBookConfig(true));
        final AddressBook inititializedAddressBook = initializer.getInitialAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the config address book.");
        assertAddressBookFileContent(initializer, configAddressBook, null, inititializedAddressBook);
    }

    @Test
    @DisplayName("Force the use of the config address book, non-null signed state")
    void forceUseOfConfigAddressBookNonNullSignedState() throws IOException {
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook();
        SignedState signedState = getMockSignedState(1);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                getMockSoftwareVersion(1),
                signedState,
                getMockSwirldStateSupplier(1),
                configAddressBook,
                getAddressBookConfig(true));
        final AddressBook inititializedAddressBook = initializer.getInitialAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the config address book.");
        assertAddressBookFileContent(
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    @Test
    @DisplayName("No state loaded from disk. Genesis State Initializes Address Book.")
    void noStateLoadedFromDisk() throws IOException {
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook();
        final AddressBook expectedAddressBook = copyWithStakeChanges(configAddressBook, 10);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                getMockSoftwareVersion(1),
                getMockSignedState(0),
                getMockSwirldStateSupplier(10),
                configAddressBook,
                getAddressBookConfig(false));
        final AddressBook inititializedAddressBook = initializer.getInitialAddressBook();
        assertEquals(
                expectedAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the expected address book.");
        assertAddressBookFileContent(initializer, configAddressBook, null, inititializedAddressBook);
    }

    @Test
    @DisplayName("No state loaded from disk. Genesis State set 0 stake.")
    void noStateLoadedFromDiskGenesisStateSetZeroStake() throws IOException {
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook();
        final AddressBookInitializer initializer = new AddressBookInitializer(
                getMockSoftwareVersion(1),
                getMockSignedState(0),
                getMockSwirldStateSupplier(0),
                configAddressBook,
                getAddressBookConfig(false));
        final AddressBook inititializedAddressBook = initializer.getInitialAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the config address book.");
        assertAddressBookFileContent(initializer, configAddressBook, null, inititializedAddressBook);
    }

    @Test
    @DisplayName("No state loaded from disk. Genesis State modifies address book entries.")
    void noStateLoadedFromDiskGenesisStateChangedAddressBook() throws IOException {
        clearTestDirectory();
        final AddressBook configAddressBook = getRandomAddressBook();
        final AddressBookInitializer initializer = new AddressBookInitializer(
                getMockSoftwareVersion(1),
                getMockSignedState(0),
                getMockSwirldStateSupplier(2),
                configAddressBook,
                getAddressBookConfig(false));
        final AddressBook inititializedAddressBook = initializer.getInitialAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the config address book.");
        assertAddressBookFileContent(initializer, configAddressBook, null, inititializedAddressBook);
    }

    @Test
    @DisplayName("Current software version is less than state software version.")
    void currentVersionLessThanStateVersion() throws IOException {
        clearTestDirectory();
        final SignedState signedState = getMockSignedState(2);
        final AddressBook configAddressBook = getRandomAddressBook();
        final SoftwareVersion configSoftwareVersion = getMockSoftwareVersion(1);
        final Supplier<SwirldState> genesisSwirldState = getMockSwirldStateSupplier(1);
        final AddressBookConfig addressBookConfig = getAddressBookConfig(false);

        assertThrows(
                IllegalStateException.class,
                () -> new AddressBookInitializer(
                        configSoftwareVersion, signedState, genesisSwirldState, configAddressBook, addressBookConfig),
                "IllegalStateException was not thrown.");
    }

    @Test
    @DisplayName("Current software version is equal to state software version.")
    void currentVersionEqualsStateVersion() throws IOException {
        clearTestDirectory();
        final SignedState signedState = getMockSignedState(2);
        final AddressBook configAddressBook = copyWithStakeChanges(signedState.getAddressBook(), 10);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                getMockSoftwareVersion(2),
                signedState,
                getMockSwirldStateSupplier(1),
                configAddressBook,
                getAddressBookConfig(false));
        final AddressBook inititializedAddressBook = initializer.getInitialAddressBook();
        assertEquals(
                signedState.getAddressBook(),
                inititializedAddressBook,
                "The initial address book must equal the state address book.");
        assertAddressBookFileContent(
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    @Test
    @DisplayName("Version upgrade, SwirldState set 0 Stake.")
    void versionUpgradeSwirldStateZeroStake() throws IOException {
        clearTestDirectory();
        final SignedState signedState = getMockSignedState(2, 0);
        final AddressBook configAddressBook = copyWithStakeChanges(signedState.getAddressBook(), 10);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                getMockSoftwareVersion(3),
                signedState,
                getMockSwirldStateSupplier(0),
                configAddressBook,
                getAddressBookConfig(false));
        final AddressBook inititializedAddressBook = initializer.getInitialAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the config address book.");
        assertAddressBookFileContent(
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    @Test
    @DisplayName("Version upgrade, Swirld State modified the address book.")
    void versionUpgradeSwirldStateModifiedAddressBook() throws IOException {
        clearTestDirectory();
        final SignedState signedState = getMockSignedState(2, 2);
        final AddressBook configAddressBook = copyWithStakeChanges(signedState.getAddressBook(), 3);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                getMockSoftwareVersion(3),
                signedState,
                getMockSwirldStateSupplier(2),
                configAddressBook,
                getAddressBookConfig(false));
        final AddressBook inititializedAddressBook = initializer.getInitialAddressBook();
        assertEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must equal the config address book.");
        assertAddressBookFileContent(
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    @Test
    @DisplayName("Version upgrade, Swirld State updates stake successfully.")
    void versionUpgradeSwirldStateStakeUpdateWorks() throws IOException {
        clearTestDirectory();
        final SignedState signedState = getMockSignedState(2);
        final AddressBook configAddressBook = copyWithStakeChanges(signedState.getAddressBook(), 5);
        final AddressBookInitializer initializer = new AddressBookInitializer(
                getMockSoftwareVersion(3),
                signedState,
                getMockSwirldStateSupplier(10),
                configAddressBook,
                getAddressBookConfig(false));
        final AddressBook inititializedAddressBook = initializer.getInitialAddressBook();
        assertNotEquals(
                configAddressBook,
                inititializedAddressBook,
                "The initial address book must not equal the config address book.");
        assertNotEquals(
                signedState.getAddressBook(),
                inititializedAddressBook,
                "The initial address book must not equal the state address book.");
        assertAddressBookFileContent(
                initializer, configAddressBook, signedState.getAddressBook(), inititializedAddressBook);
    }

    /**
     * Copies the address book while setting stake per address to the given stake value.
     *
     * @param addressBook The address book to copy
     * @param stakeValue  The new stake value per address.
     * @return the copy of the input address book with the given stake value per address.
     */
    private AddressBook copyWithStakeChanges(AddressBook addressBook, int stakeValue) {
        final AddressBook newAddressBook = new AddressBook();
        for (Address address : addressBook) {
            newAddressBook.add(address.copySetStake(stakeValue));
        }
        return newAddressBook;
    }

    /**
     * Creates a mock SoftwareVersion matching the input version number.
     *
     * @param version the integer software version.
     * @return the SoftwareVersion matching the input version.
     */
    private SoftwareVersion getMockSoftwareVersion(int version) {
        final SoftwareVersion softwareVersion = mock(SoftwareVersion.class);
        when(softwareVersion.getVersion()).thenReturn(version);
        final AtomicReference<SoftwareVersion> softVersion = new AtomicReference<>();
        when(softwareVersion.compareTo(argThat(sv -> {
                    softVersion.set(sv);
                    return true;
                })))
                .thenAnswer(i -> {
                    SoftwareVersion other = softVersion.get();
                    if (other == null) {
                        return 1;
                    } else {
                        return Integer.compare(softwareVersion.getVersion(), other.getVersion());
                    }
                });
        return softwareVersion;
    }

    /**
     * Creates a mock signed state with the given scenario and a SwirldState that sets all addresses to have stake = 7.
     *
     * @param scenario The scenario of mock signed state to create.
     * @return The mock SignedState with the input scenario loaded.
     */
    private SignedState getMockSignedState(final int scenario) {
        return getMockSignedState(scenario, 7);
    }

    /**
     * Creates a mock signed state with the given scenario and a SwirldState that sets all addresses to the given
     * stakeValue.
     *
     * @param scenario   The scenario of mock signed state to create.
     * @param stakeValue The stake value that the SwirldState should set all addresses to in its updateStake method.
     * @return The mock SignedState with the input scenario loaded and SwirldState configured to set all addresses with
     * given stakeValue.
     */
    private SignedState getMockSignedState(final int scenario, final int stakeValue) {
        final SignedState signedState = mock(SignedState.class);
        switch (scenario) {
                // case 0: null SignedState
            case 0:
                return null;
                // default case: swirldstate exists with given staking scenario
            default:
                final SoftwareVersion softwareVersion = getMockSoftwareVersion(2);
                final SwirldState swirldState =
                        getMockSwirldStateSupplier(stakeValue).get();
                final AddressBook stateAddressBook = getRandomAddressBook();
                when(signedState.getAddressBook()).thenReturn(stateAddressBook);
                when(signedState.getSwirldState()).thenReturn(swirldState);
                final PlatformData platformData = mock(PlatformData.class);
                when(platformData.getCreationSoftwareVersion()).thenReturn(softwareVersion);
                final PlatformState platformState = mock(PlatformState.class);
                when(platformState.getPlatformData()).thenReturn(platformData);
                final State state = mock(State.class);
                when(state.getPlatformState()).thenReturn(platformState);
                when(signedState.getState()).thenReturn(state);
                return signedState;
        }
    }

    /**
     * Creates a mock swirld state with the given scenario.
     *
     * @param scenario The scenario to load.
     * @return A SwirldState which behaves according to the input scenario.
     */
    private Supplier<SwirldState> getMockSwirldStateSupplier(int scenario) {

        final AtomicReference<AddressBook> configAddressBook = new AtomicReference<>();
        final SwirldState swirldState = mock(SwirldState.class);

        final OngoingStubbing<AddressBook> stub = when(swirldState.updateStake(argThat(confAB -> {
            configAddressBook.set(confAB);
            return true;
        })));

        switch (scenario) {
            case 0:
                stub.thenAnswer(foo -> copyWithStakeChanges(configAddressBook.get(), 0));
                break;
            case 1:
                stub.thenAnswer(foo -> configAddressBook.get());
                break;
            case 2:
                stub.thenAnswer(foo -> configAddressBook
                        .get()
                        .add(configAddressBook
                                .get()
                                .getAddress(0)
                                .copySetId(configAddressBook.get().getNextNodeId())));
                break;
            case 7:
                stub.thenAnswer(foo -> copyWithStakeChanges(configAddressBook.get(), 7));
                break;
            default:
                stub.thenAnswer(foo -> copyWithStakeChanges(configAddressBook.get(), 10));
        }

        return () -> swirldState;
    }

    /**
     * Creates an address book where each address has stake equal to its node ID.
     *
     * @return the address book created.
     */
    @NonNull
    private AddressBook getRandomAddressBook() {
        return new RandomAddressBookGenerator()
                .setSequentialIds(true)
                .setSize(5)
                .setCustomStakeGenerator(i -> i)
                .build();
    }

    /**
     * removes all files and subdirectories from the test directory.
     *
     * @throws IOException if any IOException is created by deleting files or directories.
     */
    private void clearTestDirectory() throws IOException {
        for (File file : testDirectory.toFile().listFiles()) {
            if (file.isDirectory()) {
                FileUtils.deleteDirectory(file.toPath());
            } else {
                file.delete();
            }
        }
    }

    /**
     * Validates the state of the recorded address book file according to the excepted input states.
     *
     * @param initializer       The AddressBookInitializer constructed.
     * @param configAddressBook The configuration address book.
     * @param stateAddressBook  The state recorded address book.  May be null if there is no state, or it was unusable.
     * @param usedAddressBook   The address book returned by the initializer.
     * @throws IOException if any IOExceptions occur while reading the recorded file on disk.
     */
    private void assertAddressBookFileContent(
            @NonNull final AddressBookInitializer initializer,
            @NonNull final AddressBook configAddressBook,
            @Nullable final AddressBook stateAddressBook,
            @NonNull final AddressBook usedAddressBook)
            throws IOException {
        Objects.requireNonNull(initializer, "The initializer must not be null.");
        Objects.requireNonNull(configAddressBook, "The configAddressBook must not be null.");
        Objects.requireNonNull(configAddressBook, "The initializedAddressBook must not be null.");
        final Path addressBookDirectory = initializer.getPathToAddressBookDirectory();
        assertEquals(
                2,
                Arrays.stream(addressBookDirectory.toFile().listFiles()).count(),
                "There should be exactly one file in the test directory.");
        final File[] files = addressBookDirectory.toFile().listFiles();
        final File debugFile;
        final File usedFile;
        if (files[0].toString().contains("debug")) {
            debugFile = files[0];
            usedFile = files[1];
        } else {
            debugFile = files[1];
            usedFile = files[0];
        }
        final String debugFileContent = Files.readString(debugFile.toPath());
        final String usedFileContent = Files.readString(usedFile.toPath());

        // check used AddressBook content
        final String usedAddressBookText = usedAddressBook.toConfigText();
        assertEquals(
                usedAddressBookText,
                usedFileContent,
                "The used file content is not the same as the used address book.");

        // check debug AddressBook content
        final String configText = CONFIG_ADDRESS_BOOK_HEADER + "\n" + configAddressBook.toConfigText();
        assertTrue(
                debugFileContent.contains(configText),
                "The configAddressBook content is not:\n" + configText + "\n\n debugFileContent:\n" + debugFileContent);

        // check stateAddressBook content
        final String stateText = stateAddressBook == null
                ? STATE_ADDRESS_BOOK_HEADER + "\n" + STATE_ADDRESS_BOOK_NULL
                : STATE_ADDRESS_BOOK_HEADER + "\n" + stateAddressBook.toConfigText();
        assertTrue(
                debugFileContent.contains(stateText),
                "The stateAddressBook content is not:\n" + stateText + "\n\n debugFileContent:\n" + debugFileContent);

        // check usedAddressBook content
        String usedText = USED_ADDRESS_BOOK_HEADER + "\n";
        if (usedAddressBook == configAddressBook) {
            usedText += CONFIG_ADDRESS_BOOK_USED;
        } else if (usedAddressBook == stateAddressBook) {
            usedText += STATE_ADDRESS_BOOK_USED;
        } else {
            usedText += usedAddressBook.toConfigText();
        }
        assertTrue(
                debugFileContent.contains(usedText),
                "The usedAddressBook content is not:\n" + usedText + "\n\n debugFileContent:\n" + debugFileContent);
    }

    /**
     * Creates an AddressBookConfig object with the given forceUseOfConfigAddressBook value.
     *
     * @param forceUseOfConfigAddressBook the value to use for the forceUseOfConfigAddressBook property.
     * @return the AddressBookConfig object.
     */
    private AddressBookConfig getAddressBookConfig(boolean forceUseOfConfigAddressBook) {
        final Configuration configuration = new TestConfigBuilder()
                .withValue("addressBook.forceUseOfConfigAddressBook", forceUseOfConfigAddressBook)
                .withValue("addressBook.addressBookDirectory", testDirectory.toString())
                .withValue("addressBook.maxRecordedAddressBookFiles", 50)
                .getOrCreateConfig();
        final AddressBookConfig addressBookConfig = configuration.getConfigData(AddressBookConfig.class);
        return addressBookConfig;
    }
}
