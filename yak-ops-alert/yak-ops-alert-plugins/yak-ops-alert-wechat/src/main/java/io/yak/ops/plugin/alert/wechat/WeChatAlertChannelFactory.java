package io.yak.ops.plugin.alert.wechat;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.alert.api.AlertChannelFactory;
import io.yak.ops.alert.api.AlertConstants;
import io.yak.ops.alert.api.AlertInputTips;
import io.yak.ops.alert.api.ShowType;
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
public final class WeChatAlertChannelFactory implements AlertChannelFactory {

    @Override
    public String name() {
        return "WeChat";
    }

    @Override
    public List<PluginParams> params() {
        InputParam corpIdParam = InputParam
                .newBuilder(WeChatAlertParamsConstants.NAME_ENTERPRISE_WE_CHAT_CORP_ID,
                        WeChatAlertParamsConstants.ENTERPRISE_WE_CHAT_CORP_ID)
                .setPlaceholder(JSONUtils.toJsonString(AlertInputTips.getAllMsg(AlertInputTips.CORP_ID)))
                .addValidate(Validate.newBuilder()
                        .setRequired(true)
                        .build())
                .build();

        InputParam secretParam = InputParam
                .newBuilder(WeChatAlertParamsConstants.NAME_ENTERPRISE_WE_CHAT_SECRET,
                        WeChatAlertParamsConstants.ENTERPRISE_WE_CHAT_SECRET)
                .setPlaceholder(JSONUtils.toJsonString(AlertInputTips.getAllMsg(AlertInputTips.SECRET)))
                .addValidate(Validate.newBuilder()
                        .setRequired(true)
                        .build())
                .build();

        InputParam usersParam = InputParam
                .newBuilder(WeChatAlertParamsConstants.NAME_ENTERPRISE_WE_CHAT_USERS,
                        WeChatAlertParamsConstants.ENTERPRISE_WE_CHAT_USERS)
                .setPlaceholder(JSONUtils.toJsonString(AlertInputTips.getAllMsg(AlertInputTips.WECHAT_MENTION_USERS)))
                .addValidate(Validate.newBuilder()
                        .setRequired(false)
                        .build())
                .build();

        InputParam agentIdParam = InputParam
                .newBuilder(WeChatAlertParamsConstants.NAME_ENTERPRISE_WE_CHAT_AGENT_ID,
                        WeChatAlertParamsConstants.ENTERPRISE_WE_CHAT_AGENT_ID)
                .setPlaceholder(JSONUtils.toJsonString(AlertInputTips.getAllMsg(AlertInputTips.WECHAT_AGENT_ID)))
                .addValidate(Validate.newBuilder()
                        .setRequired(true)
                        .build())
                .build();

        RadioParam sendType = RadioParam
                .newBuilder(WeChatAlertParamsConstants.NAME_ENTERPRISE_WE_CHAT_SEND_TYPE,
                        WeChatAlertParamsConstants.ENTERPRISE_WE_CHAT_SEND_TYPE)
                .addParamsOptions(new ParamsOptions(WeChatType.APP.getDescp(), WeChatType.APP.getDescp(), false))
                .addParamsOptions(
                        new ParamsOptions(WeChatType.APPCHAT.getDescp(), WeChatType.APPCHAT.getDescp(), false))
                .setValue(WeChatType.APP.getDescp())
                .addValidate(Validate.newBuilder().setRequired(true).build())
                .build();

        RadioParam showType = RadioParam.newBuilder(AlertConstants.NAME_SHOW_TYPE, AlertConstants.SHOW_TYPE)
                .addParamsOptions(new ParamsOptions(ShowType.MARKDOWN.getDescp(), ShowType.MARKDOWN.getDescp(), false))
                .addParamsOptions(new ParamsOptions(ShowType.TEXT.getDescp(), ShowType.TEXT.getDescp(), false))
                .setValue(ShowType.MARKDOWN.getDescp())
                .addValidate(Validate.newBuilder().setRequired(true).build())
                .build();

        return Arrays.asList(corpIdParam, secretParam, usersParam, agentIdParam, sendType, showType);
    }

    @Override
    public AlertChannel create() {
        return new WeChatAlertChannel();
    }
}
