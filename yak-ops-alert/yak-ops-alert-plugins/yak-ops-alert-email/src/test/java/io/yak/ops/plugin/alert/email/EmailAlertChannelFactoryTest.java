package io.yak.ops.plugin.alert.email;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.spi.params.base.PluginParams;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EmailAlertChannelFactoryTest {

    @Test
    public void testGetParams() {
        EmailAlertChannelFactory emailAlertChannelFactory = new EmailAlertChannelFactory();
        List<PluginParams> params = emailAlertChannelFactory.params();
        Assertions.assertEquals(12, params.size());
    }

    @Test
    public void testCreate() {
        EmailAlertChannelFactory emailAlertChannelFactory = new EmailAlertChannelFactory();
        AlertChannel alertChannel = emailAlertChannelFactory.create();
        Assertions.assertNotNull(alertChannel);
    }

}
