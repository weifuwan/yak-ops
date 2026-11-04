package io.yak.ops.extract.base.utils;

import static io.yak.ops.common.constants.Constants.COLON;

import java.io.Serializable;

import lombok.Data;
import lombok.NonNull;

@Data
public class Host implements Serializable {

    public static final Host EMPTY = new Host();

    private String ip;

    private int port;

    public Host() {
    }

    public Host(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public Host(String address) {
        int lastColonIndex = address.lastIndexOf(COLON);
        if (lastColonIndex < 0) {
            throw new IllegalArgumentException(String.format("Host : %s illegal.", address));
        }
        this.ip = address.substring(0, lastColonIndex);
        this.port = Integer.parseInt(address.substring(lastColonIndex + 1));
    }

    public String getAddress() {
        return ip + COLON + port;
    }

    public static Host of(@NonNull String address) {
        return new Host(address);
    }

}
