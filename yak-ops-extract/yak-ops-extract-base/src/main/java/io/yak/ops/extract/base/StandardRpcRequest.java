package io.yak.ops.extract.base;

import io.yak.ops.extract.base.serialize.JsonSerializer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StandardRpcRequest implements IRpcRequest {

    private byte[][] args;

    private Class<?>[] argsTypes;

    public static StandardRpcRequest of(Object[] args) {
        if (args == null || args.length == 0) {
            return new StandardRpcRequest(null, null);
        }
        final byte[][] argsBytes = new byte[args.length][];
        final Class<?>[] argsTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            argsBytes[i] = JsonSerializer.serialize(args[i]);
            argsTypes[i] = args[i] == null ? null : args[i].getClass();
        }
        return new StandardRpcRequest(argsBytes, argsTypes);
    }

}
