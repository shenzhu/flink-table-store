/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.store.connector.source;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.Path;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.binary.BinaryRowData;
import org.apache.flink.table.store.file.FileFormat;
import org.apache.flink.table.store.file.ValueKind;
import org.apache.flink.table.store.file.mergetree.MergeTreeOptions;
import org.apache.flink.table.store.file.mergetree.compact.DeduplicateAccumulator;
import org.apache.flink.table.store.file.mergetree.sst.SstFileMeta;
import org.apache.flink.table.store.file.operation.FileStoreRead;
import org.apache.flink.table.store.file.operation.FileStoreReadImpl;
import org.apache.flink.table.store.file.operation.FileStoreWriteImpl;
import org.apache.flink.table.store.file.utils.FileStorePathFactory;
import org.apache.flink.table.store.file.utils.RecordWriter;
import org.apache.flink.table.types.logical.IntType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.util.Preconditions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static java.util.Collections.singletonList;

/** Util class to read and write data for source tests. */
public class TestDataReadWrite {

    private static final RowType KEY_TYPE =
            new RowType(singletonList(new RowType.RowField("k", new IntType())));
    private static final RowType VALUE_TYPE =
            new RowType(singletonList(new RowType.RowField("v", new IntType())));
    private static final Comparator<RowData> COMPARATOR = Comparator.comparingInt(o -> o.getInt(0));

    private final FileFormat avro;
    private final FileStorePathFactory pathFactory;
    private final ExecutorService service;

    public TestDataReadWrite(String root, ExecutorService service) {
        this.avro =
                FileFormat.fromIdentifier(
                        Thread.currentThread().getContextClassLoader(),
                        "avro",
                        new Configuration());
        this.pathFactory =
                new FileStorePathFactory(new Path(root), RowType.of(new IntType()), "default");
        this.service = service;
    }

    public FileStoreRead createRead() {
        return new FileStoreReadImpl(
                KEY_TYPE, VALUE_TYPE, COMPARATOR, new DeduplicateAccumulator(), avro, pathFactory);
    }

    public List<SstFileMeta> writeFiles(
            BinaryRowData partition, int bucket, List<Tuple2<Integer, Integer>> kvs)
            throws Exception {
        Preconditions.checkNotNull(
                service, "ExecutorService must be provided if writeFiles is needed");
        RecordWriter writer = createMergeTreeWriter(partition, bucket);
        for (Tuple2<Integer, Integer> tuple2 : kvs) {
            writer.write(ValueKind.ADD, GenericRowData.of(tuple2.f0), GenericRowData.of(tuple2.f1));
        }
        List<SstFileMeta> files = writer.prepareCommit().newFiles();
        writer.close();
        return new ArrayList<>(files);
    }

    private RecordWriter createMergeTreeWriter(BinaryRowData partition, int bucket) {
        MergeTreeOptions options = new MergeTreeOptions(new Configuration());
        return new FileStoreWriteImpl(
                        KEY_TYPE,
                        VALUE_TYPE,
                        COMPARATOR,
                        new DeduplicateAccumulator(),
                        avro,
                        pathFactory,
                        null, // not used, we only create an empty writer
                        options)
                .createEmptyWriter(partition, bucket, service);
    }
}
