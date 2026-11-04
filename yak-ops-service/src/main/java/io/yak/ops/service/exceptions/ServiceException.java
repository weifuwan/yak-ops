package io.yak.ops.service.exceptions;

/**
 * Custom ZKServerException exception
 */
public class ServiceException extends RuntimeException {

    /**
     * Construct a new runtime exception with the error message
     *
     * @param errMsg Error message
     */
    public ServiceException(String errMsg) {
        super(errMsg);
    }

    /**
     * Construct a new runtime exception with the cause
     *
     * @param cause cause
     */
    public ServiceException(Throwable cause) {
        super(cause);
    }

    /**
     * Construct a new runtime exception with the detail message and cause
     *
     * @param errMsg message
     * @param cause cause
     */
    public ServiceException(String errMsg, Throwable cause) {
        super(errMsg, cause);
    }
}
