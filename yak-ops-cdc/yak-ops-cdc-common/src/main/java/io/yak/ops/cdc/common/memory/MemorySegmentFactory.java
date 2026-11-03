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

package io.yak.ops.cdc.common.memory;

import io.yak.ops.cdc.common.annotation.Internal;



import java.nio.ByteBuffer;

import static io.yak.ops.cdc.common.utils.Preconditions.checkArgument;

/** A factory for heap memory segments ({@link MemorySegment}). */
@Internal
public final class MemorySegmentFactory {

    /**
     * Creates a new memory segment that targets the given heap memory region.
     *
     * <p>This method should be used to turn short lived byte arrays into memory segments.
     *
     * @param buffer The heap memory region.
     * @return A new memory segment that targets the given heap memory region.
     */
    public static MemorySegment wrap(byte[] buffer) {
        return new MemorySegment(buffer, null);
    }

    /**
     * Copies the given heap memory region and creates a new memory segment wrapping it.
     *
     * @param bytes The heap memory region.
     * @param start starting position, inclusive
     * @param end end position, exclusive
     * @return A new memory segment that targets a copy of the given heap memory region.
     * @throws IllegalArgumentException if start > end or end > bytes.length
     */
    public static MemorySegment wrapCopy(byte[] bytes, int start, int end)
            throws IllegalArgumentException {
        checkArgument(end >= start);
        checkArgument(end <= bytes.length);
        MemorySegment copy = allocateUnpooledSegment(end - start);
        copy.put(0, bytes, start, copy.size());
        return copy;
    }

    /**
     * Wraps the four bytes representing the given number with a {@link MemorySegment}.
     *
     * @see ByteBuffer#putInt(int)
     */
    public static MemorySegment wrapInt(int value) {
        return wrap(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    /**
     * Allocates some unpooled memory and creates a new memory segment that represents that memory.
     *
     * <p>This method is similar to {@link #allocateUnpooledSegment(int, Object)}, but the memory
     * segment will have null as the owner.
     *
     * @param size The size of the memory segment to allocate.
     * @return A new memory segment, backed by unpooled heap memory.
     */
    public static MemorySegment allocateUnpooledSegment(int size) {
        return allocateUnpooledSegment(size, null);
    }

    /**
     * Allocates some unpooled memory and creates a new memory segment that represents that memory.
     *
     * <p>This method is similar to {@link #allocateUnpooledSegment(int)}, but additionally sets the
     * owner of the memory segment.
     *
     * @param size The size of the memory segment to allocate.
     * @param owner The owner to associate with the memory segment.
     * @return A new memory segment, backed by unpooled heap memory.
     */
    public static MemorySegment allocateUnpooledSegment(int size, Object owner) {
        return new MemorySegment(new byte[size], owner);
    }
}
