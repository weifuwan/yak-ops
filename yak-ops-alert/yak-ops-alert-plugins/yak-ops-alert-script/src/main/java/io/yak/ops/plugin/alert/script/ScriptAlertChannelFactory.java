package io.yak.ops.plugin.alert.script;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.alert.api.AlertChannelFactory;
import io.yak.ops.alert.api.AlertInputTips;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.spi.params.base.ParamsOptions;
import io.yak.ops.spi.params.base.PluginParams;
import io.yak.ops.spi.params.base.Validate;
import io.yak.ops.spi.params.input.InputParam;
import io.yak.ops.spi.params.radio.RadioParam;

import java.util.Arrays;
import java.util.List;

import com.google.auto.service.AutoService;

@AutoService(AlertChannelFactory.class)
public final class ScriptAlertChannelFactory implements AlertChannelFactory {

    @Override
    public String name() {
        return "Script";
    }

    @Override
    public List<PluginParams> params() {

        InputParam scriptUserParam = InputParam
                .newBuilder(ScriptParamsConstants.NAME_SCRIPT_USER_PARAMS, ScriptParamsConstants.SCRIPT_USER_PARAMS)
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .build())
                .setPlaceholder(JSONUtils.toJsonString(AlertInputTips.getAllMsg(AlertInputTips.CUSTOMIZED_PARAMS)))
                .build();
        // need check file type and file exist
        InputParam scriptPathParam =
                InputParam.newBuilder(ScriptParamsConstants.NAME_SCRIPT_PATH, ScriptParamsConstants.SCRIPT_PATH)
                        .addValidate(Validate.newBuilder()
                                .setRequired(true)
                                .build())
                        .setPlaceholder(JSONUtils.toJsonString(AlertInputTips.getAllMsg(AlertInputTips.SCRIPT_PATH)))
                        .build();

        RadioParam scriptTypeParams = RadioParam
                .newBuilder(ScriptParamsConstants.NAME_SCRIPT_TYPE, ScriptParamsConstants.SCRIPT_TYPE)
                .addParamsOptions(new ParamsOptions(ScriptType.SHELL.getDescp(), ScriptType.SHELL.getDescp(), false))
                .setValue(ScriptType.SHELL.getDescp())
                .addValidate(Validate.newBuilder().setRequired(true).build())
                .build();

        return Arrays.asList(scriptUserParam, scriptPathParam, scriptTypeParams);
    }

    @Override
    public AlertChannel create() {
        return new ScriptAlertChannel();
    }
}
