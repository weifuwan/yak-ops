package io.yak.ops.plugin.alert.feishu;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.spi.params.base.PluginParams;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FeiShuAlertChannelFactoryTest {

    @Test
    public void testGetParams() {
        FeiShuAlertChannelFactory feiShuAlertChannelFactory = new FeiShuAlertChannelFactory();
        List<PluginParams> params = feiShuAlertChannelFactory.params();
        JSONUtils.toJsonString(params);
        Assertions.assertEquals(6, params.size());
    }

    @Test
    public void testCreate() {
        FeiShuAlertChannelFactory feiShuAlertChannelFactory = new FeiShuAlertChannelFactory();
        AlertChannel alertChannel = feiShuAlertChannelFactory.create();
        Assertions.assertNotNull(alertChannel);
    }
}
