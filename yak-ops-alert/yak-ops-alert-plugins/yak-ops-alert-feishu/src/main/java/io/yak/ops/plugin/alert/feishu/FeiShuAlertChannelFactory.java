package io.yak.ops.plugin.alert.feishu;

import static io.yak.ops.common.constants.Constants.STRING_FALSE;
import static io.yak.ops.common.constants.Constants.STRING_NO;
import static io.yak.ops.common.constants.Constants.STRING_TRUE;
import static io.yak.ops.common.constants.Constants.STRING_YES;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.alert.api.AlertChannelFactory;
import io.yak.ops.alert.api.AlertInputTips;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.spi.params.base.DataType;
import io.yak.ops.spi.params.base.ParamsOptions;
import io.yak.ops.spi.params.base.PluginParams;
import io.yak.ops.spi.params.base.Validate;
import io.yak.ops.spi.params.input.InputParam;
import io.yak.ops.spi.params.input.number.InputNumberParam;
import io.yak.ops.spi.params.radio.RadioParam;

import java.util.Arrays;
import java.util.List;

import com.google.auto.service.AutoService;

@AutoService(AlertChannelFactory.class)
public final class FeiShuAlertChannelFactory implements AlertChannelFactory {

    @Override
    public String name() {
        return "Feishu";
    }

    @Override
    public List<PluginParams> params() {
        InputParam webHookParam =
                InputParam.newBuilder(FeiShuParamsConstants.NAME_WEB_HOOK, FeiShuParamsConstants.WEB_HOOK)
                        .addValidate(Validate.newBuilder()
                                .setRequired(true)
                                .build())
                        .build();
        RadioParam isEnableProxy =
                RadioParam
                        .newBuilder(FeiShuParamsConstants.NAME_FEI_SHU_PROXY_ENABLE,
                                FeiShuParamsConstants.FEI_SHU_PROXY_ENABLE)
                        .addParamsOptions(new ParamsOptions(STRING_YES, STRING_TRUE, false))
                        .addParamsOptions(new ParamsOptions(STRING_NO, STRING_FALSE, false))
                        .setValue(STRING_TRUE)
                        .addValidate(Validate.newBuilder()
                                .setRequired(false)
                                .build())
                        .build();
        InputParam proxyParam =
                InputParam.newBuilder(FeiShuParamsConstants.NAME_FEI_SHU_PROXY, FeiShuParamsConstants.FEI_SHU_PROXY)
                        .addValidate(Validate.newBuilder()
                                .setRequired(false).build())
                        .build();

        InputNumberParam portParam =
                InputNumberParam.newBuilder(FeiShuParamsConstants.NAME_FEI_SHU_PORT, FeiShuParamsConstants.FEI_SHU_PORT)
                        .addValidate(Validate.newBuilder()
                                .setRequired(false)
                                .setType(DataType.NUMBER.getDataType())
                                .build())
                        .build();

        InputParam userParam =
                InputParam.newBuilder(FeiShuParamsConstants.NAME_FEI_SHU_USER, FeiShuParamsConstants.FEI_SHU_USER)
                        .addValidate(Validate.newBuilder()
                                .setRequired(false).build())
                        .build();
        InputParam passwordParam = InputParam
                .newBuilder(FeiShuParamsConstants.NAME_FEI_SHU_PASSWORD, FeiShuParamsConstants.FEI_SHU_PASSWORD)
                .setPlaceholder(JSONUtils.toJsonString(AlertInputTips.getAllMsg(AlertInputTips.PASSWORD)))
                .setType("password")
                .build();

        return Arrays.asList(webHookParam, isEnableProxy, proxyParam, portParam, userParam, passwordParam);

    }

    @Override
    public AlertChannel create() {
        return new FeiShuAlertChannel();
    }
}
