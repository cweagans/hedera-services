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

import com.swirlds.merkledb.settings.MerkleDbSettings;
import java.time.temporal.ChronoUnit;

/**
 * Merkle DB settings that can override default settings.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near
 *     future. If you need to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public class TestMerkleDbSettings implements MerkleDbSettings {

    private final MerkleDbSettings defaultSettings;

    /** Create settings that use another as default. Individual settings can be overridden. */
    public TestMerkleDbSettings(final MerkleDbSettings defaultSettings) {
        this.defaultSettings = defaultSettings;
    }

    /** {@inheritDoc} */
    @Override
    public long getMaxNumOfKeys() {
        return defaultSettings.getMaxNumOfKeys();
    }

    /** {@inheritDoc} */
    @Override
    public long getInternalHashesRamToDiskThreshold() {
        return defaultSettings.getInternalHashesRamToDiskThreshold();
    }

    /** {@inheritDoc} */
    @Override
    public int getMediumMergeCutoffMb() {
        return defaultSettings.getMediumMergeCutoffMb();
    }

    /** {@inheritDoc} */
    @Override
    public int getSmallMergeCutoffMb() {
        return defaultSettings.getSmallMergeCutoffMb();
    }

    /** {@inheritDoc} */
    @Override
    public ChronoUnit getMergePeriodUnit() {
        return defaultSettings.getMergePeriodUnit();
    }

    /** {@inheritDoc} */
    @Override
    public int getMaxNumberOfFilesInMerge() {
        return defaultSettings.getMaxNumberOfFilesInMerge();
    }

    /** {@inheritDoc} */
    @Override
    public int getMinNumberOfFilesInMerge() {
        return defaultSettings.getMinNumberOfFilesInMerge();
    }

    /** {@inheritDoc} */
    @Override
    public long getMergeActivatePeriod() {
        return defaultSettings.getMergeActivatePeriod();
    }

    /** {@inheritDoc} */
    @Override
    public long getMediumMergePeriod() {
        return defaultSettings.getMediumMergePeriod();
    }

    /** {@inheritDoc} */
    @Override
    public long getFullMergePeriod() {
        return defaultSettings.getFullMergePeriod();
    }

    /** {@inheritDoc} */
    @Override
    public long getMaxDataFileBytes() {
        return defaultSettings.getMaxDataFileBytes();
    }

    /** {@inheritDoc} */
    @Override
    public int getMoveListChunkSize() {
        return defaultSettings.getMoveListChunkSize();
    }

    /** {@inheritDoc} */
    @Override
    public int getMaxRamUsedForMergingGb() {
        return defaultSettings.getMaxRamUsedForMergingGb();
    }

    /** {@inheritDoc} */
    @Override
    public int getIteratorInputBufferBytes() {
        return defaultSettings.getIteratorInputBufferBytes();
    }

    /** {@inheritDoc} */
    @Override
    public int getWriterOutputBufferBytes() {
        return defaultSettings.getWriterOutputBufferBytes();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReconnectKeyLeakMitigationEnabled() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public int getKeySetBloomFilterHashCount() {
        return defaultSettings.getKeySetBloomFilterHashCount();
    }

    /** {@inheritDoc} */
    @Override
    public long getKeySetBloomFilterSizeInBytes() {
        return defaultSettings.getKeySetBloomFilterSizeInBytes();
    }

    /** {@inheritDoc} */
    @Override
    public long getKeySetHalfDiskHashMapSize() {
        return defaultSettings.getKeySetHalfDiskHashMapSize();
    }

    /** {@inheritDoc} */
    @Override
    public int getKeySetHalfDiskHashMapBuffer() {
        return defaultSettings.getKeySetHalfDiskHashMapBuffer();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isIndexRebuildingEnforced() {
        return defaultSettings.isIndexRebuildingEnforced();
    }

    /** {@inheritDoc} */
    @Override
    public int getLeafRecordCacheSize() {
        return defaultSettings.getLeafRecordCacheSize();
    }

    /** {@inheritDoc} */
    @Override
    public int getReservedBufferLengthForLeafList() {
        return defaultSettings.getReservedBufferLengthForLeafList();
    }

    /** {@inheritDoc} */
    @Override
    public double getPercentHalfDiskHashMapFlushThreads() {
        return defaultSettings.getPercentHalfDiskHashMapFlushThreads();
    }

    /** {@inheritDoc} */
    @Override
    public int getNumHalfDiskHashMapFlushThreads() {
        return defaultSettings.getNumHalfDiskHashMapFlushThreads();
    }
}
