package io.yak.ops.extract.base;

public interface IRpcResponse {

    boolean isSuccess();

    String getMessage();

    byte[] getBody();

    byte[] toBytes();

}
