package org.apache.flink.cdc.connectors.mysql.utils;

import org.apache.flink.configuration.ConfigurationUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** A utility class to print configuration of connectors. */
public class OptionUtils {

    private static final Logger LOG = LoggerFactory.getLogger(OptionUtils.class);

    /** Utility class can not be instantiated. */
    private OptionUtils() {}

    public static void printOptions(String identifier, Map<String, String> config) {
        Map<String, String> hideMap = ConfigurationUtils.hideSensitiveValues(config);
        LOG.info("Print {} connector configuration:", identifier);
        for (String key : hideMap.keySet()) {
            LOG.info("{} = {}", key, hideMap.get(key));
        }
    }
}
