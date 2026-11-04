/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.alert.plugin;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.alert.api.AlertChannelFactory;
import io.yak.ops.alert.api.AlertConstants;
import io.yak.ops.common.enums.PluginType;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.dao.PluginDao;
import io.yak.ops.dao.entity.PluginDefine;
import io.yak.ops.spi.params.PluginParamsTransfer;
import io.yak.ops.spi.params.base.ParamsOptions;
import io.yak.ops.spi.params.base.PluginParams;
import io.yak.ops.spi.params.base.Validate;
import io.yak.ops.spi.params.radio.RadioParam;
import io.yak.ops.spi.plugin.PrioritySPIFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public final class AlertPluginManager {

    private final PluginDao pluginDao;

    public AlertPluginManager(PluginDao pluginDao) {
        this.pluginDao = pluginDao;
    }

    private final Map<Integer, AlertChannel> alertPluginMap = new HashMap<>();

    public void start() {
        log.info("AlertPluginManager start...");
        installAlertPlugin();
        log.info("AlertPluginManager started...");
    }

    public Optional<AlertChannel> getAlertChannel(int id) {
        return Optional.ofNullable(alertPluginMap.get(id));
    }

    private void installAlertPlugin() {
        final PluginParams warningTypeParams = getWarningTypeParams();

        PrioritySPIFactory<AlertChannelFactory> prioritySPIFactory =
                new PrioritySPIFactory<>(AlertChannelFactory.class);
        for (Map.Entry<String, AlertChannelFactory> entry : prioritySPIFactory.getSPIMap().entrySet()) {
            String name = entry.getKey();
            AlertChannelFactory factory = entry.getValue();

            final AlertChannel alertChannel = factory.create();

            final List<PluginParams> params = new ArrayList<>(factory.params());

            final String paramsJson = PluginParamsTransfer.transferParamsToJson(params);
            params.add(0, warningTypeParams);

            final PluginDefine pluginDefine = new PluginDefine(name, PluginType.ALERT.getDesc(), paramsJson);
            final int id = pluginDao.addOrUpdatePluginDefine(pluginDefine);

            alertPluginMap.put(id, alertChannel);

            log.info("Success register alert plugin: {}", name);
        }
    }

    private PluginParams getWarningTypeParams() {
        return RadioParam.newBuilder(AlertConstants.NAME_WARNING_TYPE, AlertConstants.WARNING_TYPE)
                .addParamsOptions(
                        new ParamsOptions(WarningType.SUCCESS.getDescp(), WarningType.SUCCESS.getDescp(), false))
                .addParamsOptions(
                        new ParamsOptions(WarningType.FAILURE.getDescp(), WarningType.FAILURE.getDescp(), false))
                .addParamsOptions(new ParamsOptions(WarningType.ALL.getDescp(), WarningType.ALL.getDescp(), false))
                .setValue(WarningType.ALL.getDescp())
                .addValidate(Validate.newBuilder().setRequired(true).build())
                .build();
    }
}
