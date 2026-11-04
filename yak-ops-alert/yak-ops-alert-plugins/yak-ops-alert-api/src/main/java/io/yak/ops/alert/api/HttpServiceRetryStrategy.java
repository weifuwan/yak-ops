package io.yak.ops.alert.api;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

public class HttpServiceRetryStrategy implements HttpRequestRetryHandler {

    public static final HttpServiceRetryStrategy retryStrategy = new HttpServiceRetryStrategy();

    private static final int RETRY_COUNT = 3;

    private static final long RETRY_INTERVAL_TIME = 2000L;

    @Override
    public boolean retryRequest(IOException exception, int executionCount, HttpContext httpContext) {
        if (executionCount > RETRY_COUNT) {
            return false;
        }

        if (exception instanceof SSLException) {
            return false;
        }

        if (exception instanceof UnknownHostException ||
                exception instanceof InterruptedIOException
                || exception instanceof NoHttpResponseException
                || exception instanceof SocketException) {
            // retry interval time
            try {
                Thread.sleep(RETRY_INTERVAL_TIME);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }

        HttpClientContext clientContext = HttpClientContext.adapt(httpContext);
        HttpRequest request = clientContext.getRequest();

        // Retry if the request is considered idempotent
        return !(request instanceof HttpEntityEnclosingRequest);
    }
}
