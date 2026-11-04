package io.yak.ops.common.utils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class HttpUtilsTest {

    @Test
    void testGetRequest() {
        // test HTTP URL
        String response1 = HttpUtils.get("http://www.bing.com/");
        assertNotNull(response1, "Response should not be null for a http URL");
    }

    /**
     * test invalid certification HTTPS URL
     */
    @Test
    void testGetInvalidRequest() {
        // test invalid certification HTTPS URL
        String response2 = HttpUtils.get("https://poc.bzzt.net");
        assertNull(response2,
                "Response should be null for an invalid certification https URL and throw exception in console");
    }

    /**
     * test valid certification HTTPS URL
     */
    @Test
    void testGetValidRequest() {
        String response3 = HttpUtils.get("https://www.bing.com/");
        assertNotNull(response3, "Response should not be null for a valid certification https URL");
    }

    /**
     * test wrong URL
     */
    @Test
    void testGetWrongRequest() {
        String response4 = HttpUtils.get("/abc/22");
        assertNull(response4, "Response should be null for a wrong url");
    }
}
