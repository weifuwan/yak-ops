package io.yak.ops.extract.base;

import io.yak.ops.extract.base.serialize.JsonSerializer;

import lombok.Data;

@Data
public class StandardRpcResponse implements IRpcResponse {

    private boolean success;

    private String message;

    private byte[] body;

    private Class<?> bodyType;

    public static StandardRpcResponse success(byte[] body, Class<?> bodyType) {
        StandardRpcResponse rpcResponse = new StandardRpcResponse();
        rpcResponse.setSuccess(true);
        rpcResponse.setBody(body);
        rpcResponse.setBodyType(bodyType);
        return rpcResponse;
    }

    public static StandardRpcResponse fail(String message) {
        StandardRpcResponse rpcResponse = new StandardRpcResponse();
        rpcResponse.setSuccess(false);
        rpcResponse.setMessage(message);
        return rpcResponse;
    }

    @Override
    public byte[] toBytes() {
        return JsonSerializer.serialize(this);
    }
}
