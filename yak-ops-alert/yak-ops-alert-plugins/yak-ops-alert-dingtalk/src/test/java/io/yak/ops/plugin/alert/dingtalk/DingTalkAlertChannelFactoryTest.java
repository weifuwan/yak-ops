package io.yak.ops.plugin.alert.dingtalk;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.spi.params.base.PluginParams;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DingTalkAlertChannelFactoryTest {

    @Test
    public void testGetParams() {
        DingTalkAlertChannelFactory dingTalkAlertChannelFactory = new DingTalkAlertChannelFactory();
        List<PluginParams> params = dingTalkAlertChannelFactory.params();
        JSONUtils.toJsonString(params);
        Assertions.assertEquals(12, params.size());
    }

    @Test
    public void testCreate() {
        DingTalkAlertChannelFactory dingTalkAlertChannelFactory = new DingTalkAlertChannelFactory();
        AlertChannel alertChannel = dingTalkAlertChannelFactory.create();
        Assertions.assertNotNull(alertChannel);
    }
}
