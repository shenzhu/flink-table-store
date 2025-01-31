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

package org.apache.flink.table.store.file;

import org.apache.flink.core.fs.Path;
import org.apache.flink.table.store.file.utils.FileUtils;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonGetter;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/** This file is the entrance to all data committed at some specific time point. */
public class Snapshot {

    public static final long FIRST_SNAPSHOT_ID = 1;

    private static final String FIELD_ID = "id";
    private static final String FIELD_MANIFEST_LIST = "manifestList";
    private static final String FIELD_COMMIT_USER = "commitUser";
    private static final String FIELD_COMMIT_IDENTIFIER = "commitIdentifier";
    private static final String FIELD_COMMIT_KIND = "commitKind";
    private static final String FIELD_TIME_MILLIS = "timeMillis";

    @JsonProperty(FIELD_ID)
    private final long id;

    @JsonProperty(FIELD_MANIFEST_LIST)
    private final String manifestList;

    @JsonProperty(FIELD_COMMIT_USER)
    private final String commitUser;

    // for deduplication
    @JsonProperty(FIELD_COMMIT_IDENTIFIER)
    private final String commitIdentifier;

    @JsonProperty(FIELD_COMMIT_KIND)
    private final CommitKind commitKind;

    @JsonProperty(FIELD_TIME_MILLIS)
    private final long timeMillis;

    @JsonCreator
    public Snapshot(
            @JsonProperty(FIELD_ID) long id,
            @JsonProperty(FIELD_MANIFEST_LIST) String manifestList,
            @JsonProperty(FIELD_COMMIT_USER) String commitUser,
            @JsonProperty(FIELD_COMMIT_IDENTIFIER) String commitIdentifier,
            @JsonProperty(FIELD_COMMIT_KIND) CommitKind commitKind,
            @JsonProperty(FIELD_TIME_MILLIS) long timeMillis) {
        this.id = id;
        this.manifestList = manifestList;
        this.commitUser = commitUser;
        this.commitIdentifier = commitIdentifier;
        this.commitKind = commitKind;
        this.timeMillis = timeMillis;
    }

    @JsonGetter(FIELD_ID)
    public long id() {
        return id;
    }

    @JsonGetter(FIELD_MANIFEST_LIST)
    public String manifestList() {
        return manifestList;
    }

    @JsonGetter(FIELD_COMMIT_USER)
    public String commitUser() {
        return commitUser;
    }

    @JsonGetter(FIELD_COMMIT_IDENTIFIER)
    public String commitIdentifier() {
        return commitIdentifier;
    }

    @JsonGetter(FIELD_COMMIT_KIND)
    public CommitKind commitKind() {
        return commitKind;
    }

    @JsonGetter(FIELD_TIME_MILLIS)
    public long timeMillis() {
        return timeMillis;
    }

    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static Snapshot fromJson(String json) {
        try {
            return new ObjectMapper().readValue(json, Snapshot.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static Snapshot fromPath(Path path) {
        try {
            String json = FileUtils.readFileUtf8(path);
            return Snapshot.fromJson(json);
        } catch (IOException e) {
            throw new RuntimeException("Fails to read snapshot from path " + path, e);
        }
    }

    /** Type of changes in this snapshot. */
    public enum CommitKind {

        /** Changes flushed from the mem table. */
        APPEND,

        /** Changes by compacting existing sst files. */
        COMPACT
    }
}
