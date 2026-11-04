package io.yak.ops.server.master;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.utils.placeholder.BusinessTimeUtils;
import io.yak.ops.plugin.task.api.utils.ParameterUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  user define param
 */
public class ParamsTest {

    private static final Logger logger = LoggerFactory.getLogger(ParamsTest.class);

    @Test
    public void systemParamsTest() throws Exception {
        String command = "${system.biz.date}";

        // start process
        Map<String, String> timeParams = BusinessTimeUtils
                .getBusinessTime(CommandType.START_PROCESS,
                        new Date(), null);

        command = ParameterUtils.convertParameterPlaceholders(command, timeParams);

        logger.info("start workflow : {}", command);

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, -5);

        command = "${system.biz.date}";
        // complement data
        timeParams = BusinessTimeUtils
                .getBusinessTime(CommandType.COMPLEMENT_DATA,
                        calendar.getTime(), null);
        command = ParameterUtils.convertParameterPlaceholders(command, timeParams);
        logger.info("complement data : {}", command);

    }
}
