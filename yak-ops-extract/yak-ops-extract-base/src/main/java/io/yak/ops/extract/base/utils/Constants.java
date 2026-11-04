package io.yak.ops.extract.base.utils;

import io.yak.ops.common.utils.NetUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * constant
 */
public class Constants {

    private Constants() {
        throw new IllegalStateException(Constants.class.getName());
    }

    public static final String COMMA = ",";

    public static final String SLASH = "/";

    /**
     * charset
     */
    public static final Charset UTF8 = StandardCharsets.UTF_8;

    public static final int CPUS = Runtime.getRuntime().availableProcessors();

    public static final String LOCAL_ADDRESS = NetUtils.getHost();

    public static final String OS_NAME = System.getProperty("os.name");

    public static final int WARM_UP_TIME = 10 * 60 * 1000;

}
