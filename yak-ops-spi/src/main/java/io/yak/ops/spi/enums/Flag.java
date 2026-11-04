package io.yak.ops.spi.enums;

import lombok.Getter;

/**
 * have_script
 * have_file
 * can_retry
 * have_arr_variables
 * have_map_variables
 * have_alert
 */
@Getter
public enum Flag {

    /**
     * 0 no
     * 1 yes
     */
    NO(0, "no"),
    YES(1, "yes");

    Flag(int code, String descp) {
        this.code = code;
        this.descp = descp;
    }

    private final int code;
    private final String descp;

}
