package io.yak.ops.plugin.alert.wechat;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.spi.params.base.PluginParams;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WeChatAlertChannelFactoryTest {

    @Test
    public void testGetParams() {
        WeChatAlertChannelFactory weChatAlertChannelFactory = new WeChatAlertChannelFactory();
        List<PluginParams> params = weChatAlertChannelFactory.params();
        JSONUtils.toJsonString(params);
        Assertions.assertEquals(6, params.size());
    }

    @Test
    public void testCreate() {
        WeChatAlertChannelFactory dingTalkAlertChannelFactory = new WeChatAlertChannelFactory();
        AlertChannel alertChannel = dingTalkAlertChannelFactory.create();
        Assertions.assertNotNull(alertChannel);
    }
}
