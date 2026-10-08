package io.yak.ops.core.api.common;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** 单次提交作业的不可变 128 位标识。 */
public final class JobID implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID value;

    private JobID(UUID value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public static JobID generate() {
        return new JobID(UUID.randomUUID());
    }

    /** 解析不含分隔符、恰好 32 位的十六进制字符串。 */
    public static JobID fromHexString(String hex) {
        Objects.requireNonNull(hex, "hex must not be null");
        if (!hex.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("JobID must contain exactly 32 hexadecimal digits");
        }
        String formatted = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
        return new JobID(UUID.fromString(formatted));
    }

    public String toHexString() {
        return value.toString().replace("-", "");
    }

    @Override
    public String toString() {
        return toHexString();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof JobID jobID && value.equals(jobID.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
