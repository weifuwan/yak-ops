package io.yak.ops.extract.base.protocal;

import io.yak.ops.extract.base.StandardRpcRequest;
import io.yak.ops.extract.base.StandardRpcResponse;
import io.yak.ops.extract.base.serialize.JsonSerializer;

import java.io.Serializable;

import lombok.Data;
import lombok.NonNull;

@Data
public class Transporter implements Serializable {

    private static final long serialVersionUID = -1L;

    public static final byte MAGIC = (byte) 0xbabe;
    public static final byte VERSION = 0;

    private TransporterHeader header;
    private byte[] body;

    public static Transporter of(@NonNull TransporterHeader header, StandardRpcResponse iRpcResponse) {
        return of(header, JsonSerializer.serialize(iRpcResponse));
    }

    public static Transporter of(@NonNull TransporterHeader header, StandardRpcRequest iRpcRequest) {
        return of(header, JsonSerializer.serialize(iRpcRequest));
    }

    public static Transporter of(@NonNull TransporterHeader header, byte[] body) {
        Transporter transporter = new Transporter();
        transporter.setHeader(header);
        transporter.setBody(body);
        return transporter;
    }

}
