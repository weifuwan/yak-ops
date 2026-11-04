package io.yak.ops.plugin.registry.jdbc;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.jupiter.api.Test;

class LockUtilsTest {

    @Test
    void getLockOwner() {
        assertThat(LockUtils.getLockOwner()).isNotNull();
        assertWithMessage("Lock owner should not change")
                .that(LockUtils.getLockOwner())
                .isEqualTo(LockUtils.getLockOwner());
    }
}
