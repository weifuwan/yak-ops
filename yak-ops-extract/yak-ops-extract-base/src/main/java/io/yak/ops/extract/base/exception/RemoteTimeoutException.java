package io.yak.ops.extract.base.exception;

/**
 *  timeout exception
 */
public class RemoteTimeoutException extends RemoteException {

    public RemoteTimeoutException(String message) {
        super(message);
    }

    public RemoteTimeoutException(String address, long timeoutMillis) {
        this(address, timeoutMillis, null);
    }

    public RemoteTimeoutException(String address, long timeoutMillis, Throwable cause) {
        super(String.format("wait response on the channel %s timeout %s", address, timeoutMillis), cause);
    }
}
