package io.yak.ops.extract.base.future;

public interface InvokeCallback {

    /**
     *  operation
     *
     * @param responseFuture responseFuture
     */
    void operationComplete(final ResponseFuture responseFuture);

}
