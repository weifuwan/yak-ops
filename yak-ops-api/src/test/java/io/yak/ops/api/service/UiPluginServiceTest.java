package io.yak.ops.api.service;

import static io.yak.ops.api.AssertionsHelper.assertThrowsServiceException;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.service.impl.UiPluginServiceImpl;
import io.yak.ops.common.enums.PluginType;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.PluginDefine;
import io.yak.ops.dao.mapper.PluginDefineMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.node.ArrayNode;

@ExtendWith(MockitoExtension.class)
public class UiPluginServiceTest {

    @InjectMocks
    UiPluginServiceImpl uiPluginService;

    @Mock
    PluginDefineMapper pluginDefineMapper;

    private PluginDefine pluginDefine;

    @BeforeEach
    public void before() {
        String pluginParams =
                "[{\"field\":\"receivers\",\"props\":{\"placeholder\":\"{\\\"zhMsg\\\": \\\"请输入收件人\\\",\\\"enMsg\\\": \\\"pleaseinputreceivers\\\"}\"},\"type\":\"input\"}]";
        pluginDefine = new PluginDefine("email-alert", "alert", pluginParams);
    }

    @Test
    public void testQueryPlugins1() {
        assertThrowsServiceException(Status.PLUGIN_NOT_A_UI_COMPONENT,
                () -> uiPluginService.queryUiPluginsByType(PluginType.REGISTER));
    }

    @Test
    public void testQueryPlugins2() {
        Mockito.when(pluginDefineMapper.queryByPluginType(PluginType.ALERT.getDesc())).thenReturn(null);
        assertThrowsServiceException(Status.QUERY_PLUGINS_RESULT_IS_NULL,
                () -> uiPluginService.queryUiPluginsByType(PluginType.ALERT));

        Mockito.when(pluginDefineMapper.queryByPluginType(PluginType.ALERT.getDesc()))
                .thenReturn(Collections.singletonList(pluginDefine));
        List<PluginDefine> pluginDefines = uiPluginService.queryUiPluginsByType(PluginType.ALERT);
        Assertions.assertEquals(1, pluginDefines.size());
    }

    @Test
    public void testQueryPluginDetailById() {
        Mockito.when(pluginDefineMapper.queryDetailById(1)).thenReturn(null);
        assertThrowsServiceException(Status.QUERY_PLUGIN_DETAIL_RESULT_IS_NULL,
                () -> uiPluginService.queryUiPluginDetailById(1));

        Mockito.when(pluginDefineMapper.queryDetailById(1)).thenReturn(pluginDefine);
        PluginDefine data = uiPluginService.queryUiPluginDetailById(1);
        Assertions.assertNotNull(data);

        String pluginParams = data.getPluginParams();
        ArrayNode arrayNode = JSONUtils.parseArray(pluginParams);
        String placeholder = arrayNode.path(0).path("props").path("placeholder").asText();
        Map<String, String> placeholderMap = JSONUtils.toMap(placeholder);
        Assertions.assertEquals("请输入收件人", placeholderMap.get("zhMsg"));
        Assertions.assertEquals("pleaseinputreceivers", placeholderMap.get("enMsg"));
    }

}
