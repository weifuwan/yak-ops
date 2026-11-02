/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.flink.cdc.common.serialization;

/**
 * Compatibility interface for serializer snapshots that implement the
 * deprecated serializer-instance comparison contract.
 *
 * <p>The underlying {@link TypeSerializerSnapshot} keeps both Flink 1.20
 * compatibility methods and snapshot format semantics.
 */
@Deprecated
public interface TypeSerializerSnapshotAdapter<T> extends TypeSerializerSnapshot<T> {}
