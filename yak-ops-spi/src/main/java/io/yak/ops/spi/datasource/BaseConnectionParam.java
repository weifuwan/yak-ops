package io.yak.ops.spi.datasource;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public abstract class BaseConnectionParam implements ConnectionParam {

    protected String user;

    protected String password;

    protected String address;

    protected String database;

    protected String jdbcUrl;

    protected String driverLocation;

    protected String driverClassName;

    protected String validationQuery;

    protected String compatibleMode;

    protected Map<String, String> other;

}
