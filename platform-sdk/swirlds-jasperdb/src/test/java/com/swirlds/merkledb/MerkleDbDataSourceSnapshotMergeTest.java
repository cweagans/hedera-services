/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.merkledb.MerkleDbDataSourceTest.assertLeaf;
import static com.swirlds.merkledb.MerkleDbTestUtils.checkDirectMemoryIsCleanedUpToLessThanBaseUsage;
import static com.swirlds.merkledb.MerkleDbTestUtils.getDirectMemoryUsedBytes;
import static com.swirlds.merkledb.MerkleDbTestUtils.getMetric;
import static com.swirlds.merkledb.MerkleDbTestUtils.hash;
import static com.swirlds.merkledb.files.DataFileCommon.deleteDirectoryAndContents;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.utility.Units;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MerkleDbDataSourceSnapshotMergeTest {

    private static final int COUNT = 20_000;
    private static final int COUNT2 = 30_000;

    /*
     * RUN THE TEST IN A BACKGROUND THREAD. We do this so that we can kill the thread at the end of the test which will
     * clean up all thread local caches held.
     */
    @ParameterizedTest
    @MethodSource("provideParameters")
    @Disabled
    void createMergeSnapshotReadBack(
            final TestType testType, final int internalHashesRamToDiskThreshold, final boolean preferDiskBasedIndexes)
            throws Exception {
        // Keep track of direct memory used already, so we can check if we leek over and above what we started with
        final long directMemoryUsedAtStart = getDirectMemoryUsedBytes();
        // run test in background thread
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final var future = executorService.submit(() -> {
            createMergeSnapshotReadBackImpl(testType, internalHashesRamToDiskThreshold, preferDiskBasedIndexes);
            return null;
        });
        future.get(10, TimeUnit.MINUTES);
        executorService.shutdown();
        // check we did not leak direct memory now that the thread is shut down so thread locals should be released.
        assertTrue(
                checkDirectMemoryIsCleanedUpToLessThanBaseUsage(directMemoryUsedAtStart),
                "Direct Memory used is more than base usage even after 20 gc() calls. At start was "
                        + (directMemoryUsedAtStart * Units.BYTES_TO_MEBIBYTES) + "MB and is now "
                        + (getDirectMemoryUsedBytes() * Units.BYTES_TO_MEBIBYTES)
                        + "MB");
        // check db count
        assertEquals(0, MerkleDbDataSource.getCountOfOpenDatabases(), "Expected no open dbs");
    }

    void createMergeSnapshotReadBackImpl(
            final TestType testType, final int internalHashesRamToDiskThreshold, final boolean preferDiskBasedIndexes)
            throws IOException, InterruptedException {
        final Path storeDir = Files.createTempDirectory("createMergeSnapshotReadBackImpl");
        final String tableName = "mergeSnapshotReadBack";
        final MerkleDbDataSource<VirtualLongKey, ExampleByteArrayVirtualValue> dataSource = testType.dataType()
                .createDataSource(
                        storeDir, tableName, COUNT, internalHashesRamToDiskThreshold, false, preferDiskBasedIndexes);
        final ExecutorService exec = Executors.newCachedThreadPool();
        try {
            // create some internal and leaf nodes in batches
            final int count = COUNT / 10;
            for (int batch = 0; batch < 10; batch++) {
                final int start = batch * count;
                final int end = start + count;
                System.out.printf(
                        "Creating internal nodes from %,d to %,d and leaves from %,d to %,d\n",
                        start, end - 1, COUNT + start, COUNT + end - 1);
                final int lastLeafPath = (COUNT + end) - 1;
                dataSource.saveRecords(
                        COUNT,
                        lastLeafPath,
                        IntStream.range(start, end).mapToObj(MerkleDbDataSourceTest::createVirtualInternalRecord),
                        IntStream.range(COUNT + start, COUNT + end)
                                .mapToObj(i -> testType.dataType().createVirtualLeafRecord(i)),
                        Stream.empty());
            }
            // check all data
            checkData(COUNT, testType, dataSource);
            // create snapshot and test creating a second snapshot in another thread causes exception
            final Path snapshotDir = Files.createTempDirectory("createMergeSnapshotReadBackImplSnapshot");
            final CountDownLatch countDownLatch = new CountDownLatch(3);
            exec.submit(() -> {
                // do a good snapshot
                try {
                    dataSource.getDatabase().snapshot(snapshotDir);
                } finally {
                    countDownLatch.countDown();
                }
                return null;
            });
            MILLISECONDS.sleep(1);
            exec.submit(() -> {
                // try to do a second snapshot
                try {
                    assertThrows(
                            IllegalStateException.class,
                            () -> dataSource.getDatabase().snapshot(snapshotDir),
                            "Snapshot while doing a snapshot should throw a IllegalStateException");
                } finally {
                    countDownLatch.countDown();
                }
                return null;
            });
            MILLISECONDS.sleep(1);
            exec.submit(() -> {
                // write some new data while doing snapshot
                // it is important that this sleep is long enough the snapshot on thread 0 can acquire lock but not
                // too long that it finishes and completes otherwise this test is useless.
                try {
                    // if we had COUNT2=10 then internal paths will be from 0-9 and leaf paths should be from 10-20
                    final int firstLeafPath = COUNT2;
                    final int lastLeafPathInclusive = firstLeafPath + COUNT2;
                    dataSource.saveRecords(
                            firstLeafPath,
                            lastLeafPathInclusive,
                            IntStream.range(0, firstLeafPath /* exclusive */)
                                    .mapToObj(MerkleDbDataSourceTest::createVirtualInternalRecord),
                            IntStream.range(firstLeafPath, lastLeafPathInclusive + 1 /* exclusive */)
                                    .mapToObj(i -> testType.dataType().createVirtualLeafRecord(i)),
                            Stream.empty());
                } finally {
                    countDownLatch.countDown();
                }
                return null;
            });
            assertTrue(countDownLatch.await(5, TimeUnit.SECONDS), "Timed out while waiting for threads");
            // check data in original dataSource it should have the new data written in another thread while we were
            // doing
            // the snapshot
            checkData(COUNT2, testType, dataSource);
            // load snapshot and check data
            final MerkleDbDataSource<VirtualLongKey, ExampleByteArrayVirtualValue> snapshotDataSource =
                    testType.dataType().getDataSource(snapshotDir, tableName, false);
            checkData(COUNT, testType, snapshotDataSource);
            // validate all data in the snapshot
            final DataSourceValidator<VirtualLongKey, ExampleByteArrayVirtualValue> dataSourceValidator =
                    new DataSourceValidator<>(snapshotDataSource);
            assertTrue(dataSourceValidator.validate(), "Validation of snapshot data failed.");
            // close and cleanup snapshot
            snapshotDataSource.close();
            deleteDirectoryAndContents(snapshotDir);
            // do a merge
            final AtomicBoolean merging = new AtomicBoolean(true);
            IntStream.range(0, 2).parallel().forEach(thread -> {
                if (thread == 0) { // thread 0 checks data over and over while we are merging
                    try {
                        while (merging.get()) {
                            checkData(COUNT2, testType, dataSource);
                        }
                    } catch (final IOException e) {
                        e.printStackTrace();
                    }
                } else { // thread 1 does merge
                    dataSource.doMerge();
                    merging.set(false);
                }
            });
            dataSource.doMerge();
            checkData(COUNT2, testType, dataSource);

            // check the database statistics - starting with the five speedometers
            final Metrics metrics = MerkleDbTestUtils.createMetrics();
            Metric speedometerEntry = getMetric(metrics, dataSource, "internalNodeWrites/s_");
            double meanValue = (double) speedometerEntry.get(VALUE);
            assertNotEquals(0.0, meanValue, "got mean value of 0.0 for internalNodeWrites/s_");

            speedometerEntry = getMetric(metrics, dataSource, "internalNodeReads/s_");
            meanValue = (double) speedometerEntry.get(VALUE);
            assertNotEquals(0.0, meanValue, "got mean value of 0.0 for internalNodeReads/s_");

            speedometerEntry = getMetric(metrics, dataSource, "leafWrites/s_");
            meanValue = (double) speedometerEntry.get(VALUE);
            assertNotEquals(0.0, meanValue, "got mean value of 0.0 for leafWrites/s_");

            speedometerEntry = getMetric(metrics, dataSource, "leafByKeyReads/s_");
            meanValue = (double) speedometerEntry.get(VALUE);
            assertNotEquals(0.0, meanValue, "got mean value of 0.0 for leafByKeyReads/s_");

            speedometerEntry = getMetric(metrics, dataSource, "leafByPathReads/s_", true);
            meanValue = (double) speedometerEntry.get(VALUE);
            assertNotEquals(0.0, meanValue, "got mean value of 0.0 for leafByPathReads/s_");

            // tests for the "Files" statistics
            Metric fileCountEntry = getMetric(metrics, dataSource, "internalHashFileCount_");
            int fileCount = (int) fileCountEntry.get(VALUE);
            assertNotEquals(0, fileCount, "internalHashesStoreFileCount was unexpectedly 0.");

            Metric fileSizeEntry = getMetric(metrics, dataSource, "internalHashFileSizeMb_");
            double fileSizeInMB = (double) fileSizeEntry.get(VALUE);
            assertNotEquals(0.0, fileSizeInMB, "internalHashesStoreTotalFileSizeInMB was unexpectedly 0.");

            if (testType.dataType().hasKeyToPathStore()) {
                fileCountEntry = getMetric(metrics, dataSource, "leafKeyToPathFileCount_");
                fileCount = (int) fileCountEntry.get(VALUE);
                assertNotEquals(0, fileCount, "leafKeyToPathFileCount was unexpectedly 0.");
                fileSizeEntry = getMetric(metrics, dataSource, "leafKeyToPathFileSizeMb_");
                fileSizeInMB = (double) fileSizeEntry.get(VALUE);
                assertNotEquals(0.0, fileSizeInMB, "leafKeyToPathFileSizeMb was unexpectedly 0.");
            }

            fileCountEntry = getMetric(metrics, dataSource, "leafHKVFileCount_");
            fileCount = (int) fileCountEntry.get(VALUE);
            assertNotEquals(0, fileCount, "leafHKVFileCount was unexpectedly 0.");

            fileSizeEntry = getMetric(metrics, dataSource, "leafHKVFileSizeMb_");
            fileSizeInMB = (double) fileSizeEntry.get(VALUE);
            assertNotEquals(0.0, fileSizeInMB, "leafHKVFileSizeInMB was unexpectedly 0.");

            // tests for the "Merge" statistics - only Small Merges are being performed, so Medium/Large give back 0.0
            Metric smallMergeTimeStat = getMetric(metrics, dataSource, "internalHashSmallMergeTime_");
            double smallMergeTime = (double) smallMergeTimeStat.get(VALUE);
            assertNotEquals(0.0, smallMergeTime, "internalHashesStoreSmallMergeTime was unexpectedly 0.0");

            Metric mediumMergeTimeStat = getMetric(metrics, dataSource, "internalHashMediumMergeTime_");
            double mediumMergeTime = (double) mediumMergeTimeStat.get(VALUE);
            assertEquals(0.0, mediumMergeTime, "internalHashesStoreMediumMergeTime was unexpectedly not 0.0");

            Metric largeMergeTimeStat = getMetric(metrics, dataSource, "internalHashLargeMergeTime_");
            double largeMergeTime = (double) largeMergeTimeStat.get(VALUE);
            assertEquals(0.0, largeMergeTime, "internalHashesStoreLargeMergeTime was unexpectedly not 0.0");

            if (testType.dataType().hasKeyToPathStore()) {
                smallMergeTimeStat = getMetric(metrics, dataSource, "leafKeyToPathSmallMergeTime_", true);
                smallMergeTime = (double) smallMergeTimeStat.get(VALUE);
                assertNotEquals(0.0, smallMergeTime, "leafKeyToPathStoreSmallMergeTime was unexpectedly 0.0");
                mediumMergeTimeStat = getMetric(metrics, dataSource, "leafKeyToPathMediumMergeTime_", true);
                mediumMergeTime = (double) mediumMergeTimeStat.get(VALUE);
                assertEquals(0.0, mediumMergeTime, "leafKeyToPathStoreMediumMergeTime was unexpectedly not 0.0");
                largeMergeTimeStat = getMetric(metrics, dataSource, "leafKeyToPathLargeMergeTime_", true);
                largeMergeTime = (double) largeMergeTimeStat.get(VALUE);
                assertEquals(0.0, largeMergeTime, "leafKeyToPathStoreLargeMergeTime was unexpectedly not 0.0");
            }

            smallMergeTimeStat = getMetric(metrics, dataSource, "leafHKVSmallMergeTime_");
            smallMergeTime = (double) smallMergeTimeStat.get(VALUE);
            assertNotEquals(0.0, smallMergeTime, "leafPathToHashKeyValueStoreSmallMergeTime was unexpectedly 0.0");

            mediumMergeTimeStat = getMetric(metrics, dataSource, "leafHKVMediumMergeTime_");
            mediumMergeTime = (double) mediumMergeTimeStat.get(VALUE);
            assertEquals(0.0, mediumMergeTime, "leafPathToHashKeyValueStoreMediumMergeTime was unexpectedly not 0.0");

            largeMergeTimeStat = getMetric(metrics, dataSource, "leafHKVLargeMergeTime_");
            largeMergeTime = (double) largeMergeTimeStat.get(VALUE);
            assertEquals(0.0, largeMergeTime, "leafPathToHashKeyValueStoreLargeMergeTime was unexpectedly not 0.0");
        } finally {
            // cleanup
            dataSource.close();
            deleteDirectoryAndContents(storeDir);
            exec.shutdown();
        }
    }

    private static Stream<Arguments> provideParameters() {
        final ArrayList<Arguments> arguments = new ArrayList<>(TestType.values().length * 3);
        final int[] ramDiskSplitOptions = new int[] {0, COUNT / 2, Integer.MAX_VALUE};
        for (final TestType testType : TestType.values()) {
            for (final int ramDiskSplit : ramDiskSplitOptions) {
                arguments.add(Arguments.of(testType, ramDiskSplit, false));
                arguments.add(Arguments.of(testType, ramDiskSplit, true));
            }
        }
        return arguments.stream();
    }

    private static void checkData(
            final int count,
            final TestType testType,
            final MerkleDbDataSource<VirtualLongKey, ExampleByteArrayVirtualValue> dataSource)
            throws IOException {
        System.out.println("checking internal nodes 0 to " + (count - 1) + " and leaves from " + count + " to "
                + ((count * 2) - 1));
        // check all the node hashes
        for (int i = 0; i < count; i++) {
            assertEquals(
                    hash(i),
                    dataSource.loadInternalRecord(i).getHash(),
                    "The hash should not have changed since it was created");
        }
        // check all the leaf data
        for (int i = count; i < (count * 2); i++) {
            assertLeaf(testType, dataSource, i, i);
        }
    }
}
