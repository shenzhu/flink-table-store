/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.store.file.mergetree.sst;

import org.apache.flink.connector.file.src.FileSourceSplit;
import org.apache.flink.connector.file.src.reader.BulkFormat;
import org.apache.flink.connector.file.src.util.RecordAndPosition;
import org.apache.flink.core.fs.Path;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.binary.BinaryRowData;
import org.apache.flink.table.store.file.FileFormat;
import org.apache.flink.table.store.file.KeyValue;
import org.apache.flink.table.store.file.KeyValueSerializer;
import org.apache.flink.table.store.file.utils.FileStorePathFactory;
import org.apache.flink.table.store.file.utils.FileUtils;
import org.apache.flink.table.store.file.utils.RecordReader;
import org.apache.flink.table.types.logical.RowType;

import javax.annotation.Nullable;

import java.io.IOException;

/** Reads {@link KeyValue}s from sst files. */
public class SstFileReader {

    private final RowType keyType;
    private final RowType valueType;
    private final BulkFormat<RowData, FileSourceSplit> readerFactory;
    private final SstPathFactory pathFactory;

    private SstFileReader(
            RowType keyType,
            RowType valueType,
            BulkFormat<RowData, FileSourceSplit> readerFactory,
            SstPathFactory pathFactory) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.readerFactory = readerFactory;
        this.pathFactory = pathFactory;
    }

    public RecordReader read(String fileName) throws IOException {
        return new SstFileRecordReader(pathFactory.toPath(fileName));
    }

    private class SstFileRecordReader implements RecordReader {

        private final BulkFormat.Reader<RowData> reader;
        private final KeyValueSerializer serializer;

        private SstFileRecordReader(Path path) throws IOException {
            long fileSize = FileUtils.getFileSize(path);
            FileSourceSplit split = new FileSourceSplit("ignore", path, 0, fileSize, 0, fileSize);
            this.reader = readerFactory.createReader(FileUtils.DEFAULT_READER_CONFIG, split);
            this.serializer = new KeyValueSerializer(keyType, valueType);
        }

        @Nullable
        @Override
        public RecordIterator readBatch() throws IOException {
            BulkFormat.RecordIterator<RowData> iterator = reader.readBatch();
            return iterator == null ? null : new SstFileRecordIterator(iterator, serializer);
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    private static class SstFileRecordIterator implements RecordReader.RecordIterator {

        private final BulkFormat.RecordIterator<RowData> iterator;
        private final KeyValueSerializer serializer;

        private SstFileRecordIterator(
                BulkFormat.RecordIterator<RowData> iterator, KeyValueSerializer serializer) {
            this.iterator = iterator;
            this.serializer = serializer;
        }

        @Override
        public KeyValue next() throws IOException {
            RecordAndPosition<RowData> result = iterator.next();
            return result == null ? null : serializer.fromRow(result.getRecord());
        }

        @Override
        public void releaseBatch() {
            iterator.releaseBatch();
        }
    }

    /** Creates {@link SstFileReader}. */
    public static class Factory {

        private final RowType keyType;
        private final RowType valueType;
        private final FileStorePathFactory pathFactory;
        private final BulkFormat<RowData, FileSourceSplit> readerFactory;

        public Factory(
                RowType keyType,
                RowType valueType,
                FileFormat fileFormat,
                FileStorePathFactory pathFactory) {
            this.keyType = keyType;
            this.valueType = valueType;
            this.pathFactory = pathFactory;
            RowType recordType = KeyValue.schema(keyType, valueType);
            this.readerFactory = fileFormat.createReaderFactory(recordType);
        }

        public SstFileReader create(BinaryRowData partition, int bucket) {
            return new SstFileReader(
                    keyType,
                    valueType,
                    readerFactory,
                    pathFactory.createSstPathFactory(partition, bucket));
        }
    }
}
