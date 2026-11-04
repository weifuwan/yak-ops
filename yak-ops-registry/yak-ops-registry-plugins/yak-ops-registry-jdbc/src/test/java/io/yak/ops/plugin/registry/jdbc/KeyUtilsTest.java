package io.yak.ops.plugin.registry.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyUtilsTest {

    @Test
    void isParent() {
        assertFalse(KeyUtils.isParent("/a", "/b"));
        assertFalse(KeyUtils.isParent("/a", "/a"));
        assertFalse(KeyUtils.isParent("/b/c", "/b"));
        assertFalse(KeyUtils.isParent("/b/c", "/b/"));

        assertTrue(KeyUtils.isParent("/", "/b"));
        assertTrue(KeyUtils.isParent("/b/c", "/b/c/d"));
        assertTrue(KeyUtils.isParent("/b", "/b/c/d"));
        assertTrue(KeyUtils.isParent("/b/", "/b/c/d"));

    }

}
