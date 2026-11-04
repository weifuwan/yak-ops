package io.yak.ops.extract.base.protocal;

import io.yak.ops.extract.base.exception.RemoteException;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

@Sharable
public class TransporterEncoder extends MessageToByteEncoder<Transporter> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Transporter transporter, ByteBuf out) {
        if (transporter == null) {
            throw new RemoteException("encode msg is null");
        }
        out.writeByte(Transporter.MAGIC);
        out.writeByte(Transporter.VERSION);

        // write header
        byte[] header = transporter.getHeader().toBytes();
        out.writeInt(header.length);
        out.writeBytes(header);

        // write body
        byte[] body = transporter.getBody();
        out.writeInt(body.length);
        out.writeBytes(body);
    }

}
