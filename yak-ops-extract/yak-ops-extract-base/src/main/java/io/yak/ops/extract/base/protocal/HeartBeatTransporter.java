package io.yak.ops.extract.base.protocal;

import io.yak.ops.extract.base.StandardRpcResponse;

public class HeartBeatTransporter extends Transporter {

    public static final String METHOD_IDENTIFY = "HEART_BEAT";

    private static final Transporter HEART_BEAT_TRANSPORTER =
            of(TransporterHeader.of(METHOD_IDENTIFY), new StandardRpcResponse());

    public static Transporter getHeartBeatTransporter() {
        return HEART_BEAT_TRANSPORTER;
    }

}
