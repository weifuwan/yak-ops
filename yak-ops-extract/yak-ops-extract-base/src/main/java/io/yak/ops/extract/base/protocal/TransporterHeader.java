package io.yak.ops.extract.base.protocal;

import io.yak.ops.extract.base.serialize.JsonSerializer;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

import lombok.Data;

@Data
public class TransporterHeader implements Serializable {

    private static final long serialVersionUID = -1L;

    private static final AtomicLong REQUEST_ID = new AtomicLong(1);

    private String methodIdentifier;
    private long opaque;

    // Used for JsonDeSerializer
    public TransporterHeader() {

    }

    public TransporterHeader(String methodIdentifier) {
        this(REQUEST_ID.getAndIncrement(), methodIdentifier);
    }

    public TransporterHeader(long opaque, String methodIdentifier) {
        this.opaque = opaque;
        this.methodIdentifier = methodIdentifier;
    }

    public static TransporterHeader of(String methodIdentifier) {
        return new TransporterHeader(methodIdentifier);
    }

    public static TransporterHeader of(long opaque, String methodIdentifier) {
        return new TransporterHeader(opaque, methodIdentifier);
    }

    public byte[] toBytes() {
        return JsonSerializer.serialize(this);
    }

}
