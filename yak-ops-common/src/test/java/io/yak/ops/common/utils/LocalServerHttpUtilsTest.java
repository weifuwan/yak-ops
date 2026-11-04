package io.yak.ops.common.utils;

import io.yak.ops.common.constants.Constants;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ObjectNode;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class LocalServerHttpUtilsTest extends TestCase {

    public static final Logger logger = LoggerFactory.getLogger(LocalServerHttpUtilsTest.class);
    private static LocalJettyHttpServer server = null;

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTestSuite(LocalServerHttpUtilsTest.class);
        server = new LocalJettyHttpServer(suite);
        return server;
    }

    public void testGetTest() throws Exception {
        // success
        String result = null;
        result = HttpUtils.get("http://localhost:" + server.getServerPort() + "/test.json");
        Assertions.assertNotNull(result);
        ObjectNode jsonObject = JSONUtils.parseObject(result);
        Assertions.assertEquals("Github", jsonObject.path("name").asText());
        result = HttpUtils.get("http://123.333.111.33/ccc");
        Assertions.assertNull(result);
    }

    public void testGetResponseContentString() {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpget = new HttpGet("http://localhost:" + server.getServerPort() + "/test.json");
        /** set timeout、request time、socket timeout */
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(Constants.HTTP_CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(Constants.HTTP_CONNECTION_REQUEST_TIMEOUT)
                .setSocketTimeout(Constants.SOCKET_TIMEOUT).setRedirectsEnabled(true).build();
        httpget.setConfig(requestConfig);

        String responseContent = null;
        responseContent = HttpUtils.getResponseContentString(httpget, httpclient);
        Assertions.assertNotNull(responseContent);

        responseContent = HttpUtils.getResponseContentString(null, httpclient);
        Assertions.assertNull(responseContent);

        responseContent = HttpUtils.getResponseContentString(httpget, null);
        Assertions.assertNull(responseContent);
    }

    public void testGetHttpClient() {
        CloseableHttpClient httpClient1 = HttpUtils.getInstance();
        CloseableHttpClient httpClient2 = HttpUtils.getInstance();
        Assertions.assertEquals(httpClient1, httpClient2);
    }

    public void testKerberosHttpsGet() {
        logger.info(PropertyUtils.getString(Constants.LOGIN_USER_KEY_TAB_USERNAME));
        logger.info(PropertyUtils.getString(Constants.LOGIN_USER_KEY_TAB_PATH));
        logger.info(PropertyUtils.getString(Constants.JAVA_SECURITY_KRB5_CONF_PATH));
        String url = "https://www.apache.org/";
        logger.info(KerberosHttpClient.get(url));
        Assertions.assertTrue(true);
    }

    public void testHttpsGet() {
        String url = "https://www.apache.org/";
        logger.info(HttpUtils.get(url));
        Assertions.assertTrue(true);
    }

    public void testHttpGet() {
        String url = "http://www.apache.org/";
        logger.info(HttpUtils.get(url));
        Assertions.assertTrue(true);
    }

}
