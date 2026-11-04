package io.yak.ops.spi.params;

import static io.yak.ops.common.constants.Constants.STRING_PLUGIN_PARAM_FIELD;
import static io.yak.ops.common.constants.Constants.STRING_PLUGIN_PARAM_VALUE;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.spi.params.base.PluginParams;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * plugin params pojo and json transfer tool
 */
public class PluginParamsTransfer {

    public static String transferParamsToJson(List<PluginParams> list) {
        return JSONUtils.toJsonString(list);
    }

    public static List<PluginParams> transferJsonToParamsList(String str) {
        return JSONUtils.toList(str, PluginParams.class);
    }

    /**
     * return the plugin params map
     */
    public static Map<String, String> getPluginParamsMap(String paramsJsonStr) {
        List<PluginParams> pluginParams = transferJsonToParamsList(paramsJsonStr);
        Map<String, String> paramsMap = new HashMap<>();
        for (PluginParams param : pluginParams) {
            paramsMap.put(param.getName(), param.getValue() == null ? null : param.getValue().toString());
        }
        return paramsMap;
    }

    /**
     * generate Plugin Params
     *
     * @param paramsJsonStr paramsJsonStr value
     * @param pluginParamsTemplate pluginParamsTemplate
     * @return return plugin params value
     */
    public static List<Map<String, Object>> generatePluginParams(String paramsJsonStr, String pluginParamsTemplate) {
        Map<String, Object> paramsMap = JSONUtils.parseObject(paramsJsonStr, new TypeReference<Map<String, Object>>() {
        });
        return generatePluginParams(paramsMap, pluginParamsTemplate);
    }

    /**
     * generate Plugin Params
     *
     * @param paramsMap paramsMap
     * @param pluginParamsTemplate pluginParamsTemplate
     * @return return plugin params value
     */
    public static List<Map<String, Object>> generatePluginParams(Map<String, Object> paramsMap,
                                                                 String pluginParamsTemplate) {
        if (paramsMap == null || paramsMap.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> pluginParamsList =
                JSONUtils.parseObject(pluginParamsTemplate, new TypeReference<List<Map<String, Object>>>() {
                });
        pluginParamsList.forEach(pluginParams -> pluginParams.put(STRING_PLUGIN_PARAM_VALUE,
                paramsMap.get(pluginParams.get(STRING_PLUGIN_PARAM_FIELD))));
        return pluginParamsList;
    }
}
