package io.yak.ops.flow.runtime.support;

import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;

/** Versioned serializer for self-contained in-memory SourceSplit fixtures in Runtime tests. */
public final class TestSplitSerializers {

    private TestSplitSerializers() {}

    public static <T extends SourceSplit> SimpleVersionedSerializer<T> utf8(
            Function<T, String> encode, Function<String, T> decode) {
        Objects.requireNonNull(encode);
        Objects.requireNonNull(decode);
        return new SimpleVersionedSerializer<>() {
            @Override
            public int getVersion() {
                return 1;
            }

            @Override
            public byte[] serialize(T split) {
                return encode.apply(split).getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public T deserialize(int version, byte[] data) throws IOException {
                if (version != 1) {
                    throw new IOException("Unsupported test SourceSplit version: " + version);
                }
                return Objects.requireNonNull(decode.apply(new String(data, StandardCharsets.UTF_8)));
            }
        };
    }
}
