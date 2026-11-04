package io.yak.ops.plugin.datasource.ssh.param;

import io.yak.ops.spi.datasource.ConnectionParam;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SSHConnectionParam implements ConnectionParam {

    protected String user;

    protected String password;

    protected String privateKey;

    protected String host;

    protected int port = 22;
}
