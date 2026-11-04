package io.yak.ops.extract.base.utils;

import org.junit.jupiter.api.Test;

import com.google.common.truth.Truth;

class HostTest {

    @Test
    void testEquals() {
        Truth.assertThat(Host.of("localhost:8080")).isEqualTo(Host.of("localhost:8080"));
    }

}
