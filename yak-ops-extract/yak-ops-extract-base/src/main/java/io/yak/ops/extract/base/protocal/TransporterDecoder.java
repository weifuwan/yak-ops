package io.yak.ops.extract.base.protocal;

import io.yak.ops.extract.base.serialize.JsonSerializer;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransporterDecoder extends ReplayingDecoder<TransporterDecoder.State> {

    public TransporterDecoder() {
        super(State.MAGIC);
    }

    private int headerLength;
    private byte[] header;
    private int bodyLength;
    private byte[] body;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case MAGIC:
                checkMagic(in.readByte());
                checkpoint(State.VERSION);
            case VERSION:
                checkVersion(in.readByte());
                checkpoint(State.HEADER_LENGTH);
            case HEADER_LENGTH:
                headerLength = in.readInt();
                checkpoint(State.HEADER);
            case HEADER:
                header = new byte[headerLength];
                in.readBytes(header);
                checkpoint(State.BODY_LENGTH);
            case BODY_LENGTH:
                bodyLength = in.readInt();
                checkpoint(State.BODY);
            case BODY:
                body = new byte[bodyLength];
                in.readBytes(body);
                Transporter transporter =
                        Transporter.of(JsonSerializer.deserialize(header, TransporterHeader.class), body);
                out.add(transporter);
                checkpoint(State.MAGIC);
                break;
            default:
                log.warn("unknown decoder state {}", state());
        }
    }

    private void checkMagic(byte magic) {
        if (magic != Transporter.MAGIC) {
            throw new IllegalArgumentException("illegal packet [magic]" + magic);
        }
    }

    private void checkVersion(byte version) {
        if (version != Transporter.VERSION) {
            throw new IllegalArgumentException("illegal protocol [version]" + version);
        }
    }

    enum State {
        MAGIC,
        VERSION,
        HEADER_LENGTH,
        HEADER,
        BODY_LENGTH,
        BODY;
    }

}
