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

package com.hedera.node.app.service.network.impl.serdes;

import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MonoRunningHashesAdapterCodec implements Codec<RecordsRunningHashLeaf> {
    @NonNull
    @Override
    public RecordsRunningHashLeaf parse(final @NonNull ReadableSequentialData input) throws IOException {
        final var length = input.readInt();
        final var javaIn = new byte[length];
        input.readBytes(javaIn);
        final var bais = new ByteArrayInputStream(javaIn);
        final var context = new RecordsRunningHashLeaf();
        final var hashLeafIn = new SerializableDataInputStream(bais);
        context.deserialize(hashLeafIn, RecordsRunningHashLeaf.RELEASE_0280_VERSION);
        return context;
    }

    @Override
    public void write(final @NonNull RecordsRunningHashLeaf item, final @NonNull WritableSequentialData output)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SerializableDataOutputStream sdo = new SerializableDataOutputStream(baos);
        item.serialize(sdo);
        sdo.flush();
        output.writeInt(baos.toByteArray().length);
        output.writeBytes(baos.toByteArray());
    }

    @Override
    public int measure(@NonNull ReadableSequentialData input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int measureRecord(final @NonNull RecordsRunningHashLeaf recordsRunningHashLeaf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fastEquals(@NonNull RecordsRunningHashLeaf item, @NonNull ReadableSequentialData input) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public RecordsRunningHashLeaf parseStrict(@NonNull ReadableSequentialData dataInput) throws IOException {
        return parse(dataInput);
    }
}
