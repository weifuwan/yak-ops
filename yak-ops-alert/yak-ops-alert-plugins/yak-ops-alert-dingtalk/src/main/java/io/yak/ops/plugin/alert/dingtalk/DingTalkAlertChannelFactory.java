package io.yak.ops.plugin.alert.dingtalk;

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
public final class DingTalkAlertChannelFactory implements AlertChannelFactory {

    @Override
    public String name() {
        return "DingTalk";
    }

    @Override
    public List<PluginParams> params() {
        InputParam webHookParam = InputParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_WEB_HOOK, DingTalkParamsConstants.DING_TALK_WEB_HOOK)
                .addValidate(Validate.newBuilder()
                        .setRequired(true)
                        .build())
                .build();
        InputParam keywordParam = InputParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_KEYWORD, DingTalkParamsConstants.DING_TALK_KEYWORD)
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .build())
                .build();

        InputParam secretParam = InputParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_SECRET, DingTalkParamsConstants.DING_TALK_SECRET)
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .build())
                .build();

        RadioParam msgTypeParam = RadioParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_MSG_TYPE, DingTalkParamsConstants.DING_TALK_MSG_TYPE)
                .addParamsOptions(new ParamsOptions(DingTalkParamsConstants.DING_TALK_MSG_TYPE_TEXT,
                        DingTalkParamsConstants.DING_TALK_MSG_TYPE_TEXT, false))
                .addParamsOptions(new ParamsOptions(DingTalkParamsConstants.DING_TALK_MSG_TYPE_MARKDOWN,
                        DingTalkParamsConstants.DING_TALK_MSG_TYPE_MARKDOWN, false))
                .setValue(DingTalkParamsConstants.DING_TALK_MSG_TYPE_TEXT)
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .build())
                .build();

        InputParam atMobilesParam = InputParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_AT_MOBILES,
                        DingTalkParamsConstants.DING_TALK_AT_MOBILES)
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .build())
                .build();
        InputParam atDingtalkIdsParam = InputParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_AT_USERIDS,
                        DingTalkParamsConstants.DING_TALK_AT_USERIDS)
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .build())
                .build();
        RadioParam isAtAll = RadioParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_AT_ALL, DingTalkParamsConstants.DING_TALK_AT_ALL)
                .addParamsOptions(new ParamsOptions(STRING_YES, STRING_TRUE, false))
                .addParamsOptions(new ParamsOptions(STRING_NO, STRING_FALSE, false))
                .setValue(STRING_FALSE)
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .build())
                .build();

        RadioParam isEnableProxy = RadioParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_PROXY_ENABLE,
                        DingTalkParamsConstants.DING_TALK_PROXY_ENABLE)
                .addParamsOptions(new ParamsOptions(STRING_YES, STRING_TRUE, false))
                .addParamsOptions(new ParamsOptions(STRING_NO, STRING_FALSE, false))
                .setValue(STRING_FALSE)
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .build())
                .build();
        InputParam proxyParam = InputParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_PROXY, DingTalkParamsConstants.DING_TALK_PROXY)
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .build())
                .build();

        InputNumberParam portParam = InputNumberParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_PORT, DingTalkParamsConstants.DING_TALK_PORT)
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .setType(DataType.NUMBER.getDataType())
                        .build())
                .build();

        InputParam userParam = InputParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_USER, DingTalkParamsConstants.DING_TALK_USER)
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .build())
                .build();
        InputParam passwordParam = InputParam
                .newBuilder(DingTalkParamsConstants.NAME_DING_TALK_PASSWORD, DingTalkParamsConstants.DING_TALK_PASSWORD)
                .setPlaceholder(JSONUtils.toJsonString(AlertInputTips.getAllMsg(AlertInputTips.PASSWORD)))
                .setType("password")
                .build();

        return Arrays.asList(webHookParam, keywordParam, secretParam, msgTypeParam, atMobilesParam, atDingtalkIdsParam,
                isAtAll, isEnableProxy, proxyParam, portParam, userParam, passwordParam);
    }

    @Override
    public AlertChannel create() {
        return new DingTalkAlertChannel();
    }
}
