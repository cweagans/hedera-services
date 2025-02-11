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

package com.swirlds.merkledb.files.hashmap;

import com.swirlds.virtualmap.VirtualKey;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HalfDiskHashMap buckets are somewhat expensive resources. Every bucket has an
 * underlying byte buffer to store bucket data and metadata, and the number of
 * buckets is huge. This class provides a bucket pool, so buckets can be reused
 * rather than created on every read / write call.
 *
 * <p>Bucket pool is accessed from multiple threads:
 * <ul>
 *     <li>Transaction thread, when a key path is loaded from HDHM as a part of
 *     get or getForModify call</li>
 *     <li>Lifecycle thread, when updated bucket is written to disk in the end
 *     of HDHM flushing</li>
 *     <li>HDHM background bucket reading threads</li>
 *     <li>Warmup (aka prefetch) threads</li>
 * </ul>
 *
 * <p>If buckets were created, updated, and then released (marked as available
 * for other threads) on a single thread, this class would be as simple as a
 * single {@link ThreadLocal} object. This is not the case, unfortunately. For
 * example, when HDHM background reading threads read buckets from disk, buckets
 * are requested from the pool by {@link BucketSerializer} as a part of data
 * file collection read call. Then buckets are updated and put to a queue, which
 * is processed on a different thread, virtual pipeline (aka lifecycle) thread.
 * Only after that buckets can be reused. This is why the pool is implemented as
 * an array of buckets with fast concurrent read/write access from multiple
 * threads.
 */
public class ReusableBucketPool<K extends VirtualKey> {

    /** Default number of reusable buckets in this pool */
    private static final int DEFAULT_POOL_SIZE = 8192;

    /** Pool size */
    private final int poolSize;

    /** Buckets */
    private final AtomicReferenceArray<Bucket<K>> buckets;
    /**
     * To avoid synchronization on a single object, every bucket has its own lock. The
     * locks are used to wait, if the pool is empty (no available buckets), or to wake
     * up the waiting thread, when a bucket is released back to the pool.
     */
    private final AtomicReferenceArray<Lock> locks;
    /** Condition objects for the locks above, to await and signal on */
    private final AtomicReferenceArray<Condition> conditions;

    /**
     * Index in the array of the next available (non-null) bucket. When a bucket is
     * requested from the pool, it's served from this index, and the corresponding
     * array element is set to null.
     *
     * <p>This index may point to an empty element in the array. It happens, when the
     * pool is empty. In this case, the thread that requests a new bucket from will be
     * blocked, until a bucket is released into the array at the index.
     */
    private final AtomicInteger nextIndexToGet = new AtomicInteger(0);
    /**
     * Index in the array of the next empty slot. When a bucket is released to the pool,
     * it's put to the array at this index, and the index is increased by one. If another
     * thread is waiting for a bucket to be released at the given index, it is notified.
     */
    private final AtomicInteger nextIndexToRelease = new AtomicInteger(0);

    /**
     * Creates a new reusable bucket pool of the default size.
     *
     * @param serializer Key serializer used by the buckets in the pool
     */
    public ReusableBucketPool(final BucketSerializer<K> serializer) {
        this(DEFAULT_POOL_SIZE, serializer);
    }

    /**
     * Creates a new reusable bucket pool of the specified size.
     *
     * @param serializer Key serializer used by the buckets in the pool
     */
    public ReusableBucketPool(final int size, final BucketSerializer<K> serializer) {
        poolSize = size;
        buckets = new AtomicReferenceArray<>(poolSize);
        locks = new AtomicReferenceArray<>(poolSize);
        conditions = new AtomicReferenceArray<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            buckets.set(i, new Bucket<>(serializer.getKeySerializer(), this));
            final Lock lock = new ReentrantLock();
            locks.set(i, lock);
            conditions.set(i, lock.newCondition());
        }
    }

    /**
     * Gets a bucket from the pool. If the pool is empty, the calling thread waits
     * until a bucket is released to the pool.
     *
     * @return A bucket that can be used for reads / writes until it's released back
     * to the pool
     */
    public Bucket<K> getBucket() {
        // Every call to this method is bound to a particular index in the pool
        final int index = nextIndexToGet.getAndUpdate(t -> (t + 1) % poolSize);
        // Try optimistic get first
        Bucket<K> bucket = buckets.getAndSet(index, null);
        if (bucket == null) {
            final Lock lock = locks.get(index);
            final Condition cond = conditions.get(index);
            lock.lock();
            try {
                bucket = buckets.getAndSet(index, null);
                // Wait until a bucket at the given index is released
                while (bucket == null) {
                    try {
                        cond.await();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    bucket = buckets.getAndSet(index, null);
                }
            } finally {
                lock.unlock();
            }
        }
        bucket.clear();
        return bucket;
    }

    /**
     * Releases a bucket back to this pool. The bucket cannot be used after this
     * call, until it's borrowed from the pool again using {@link #getBucket()}.
     *
     * @param bucket A bucket to release to this pool
     */
    public void releaseBucket(final Bucket<K> bucket) {
        assert bucket.getBucketPool() == this;
        int index = nextIndexToRelease.getAndUpdate(t -> (t + 1) % poolSize);
        boolean released = buckets.compareAndSet(index, null, bucket);
        while (!released) {
            Thread.onSpinWait();
            released = buckets.compareAndSet(index, null, bucket);
        }
        final Lock lock = locks.get(index);
        final Condition cond = conditions.get(index);
        lock.lock();
        try {
            cond.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
