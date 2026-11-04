package io.yak.ops.common.model;

import java.util.Date;

import lombok.Data;

@Data
public class Server {

    private int id;

    private String host;

    private int port;

    private String serverDirectory;

    private String heartBeatInfo;

    private Date createTime;

    private Date lastHeartbeatTime;

}
