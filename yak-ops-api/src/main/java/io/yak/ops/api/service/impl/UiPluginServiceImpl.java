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

package io.yak.ops.api.service.impl;

import io.yak.ops.api.dto.ProductInfoDto;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.service.UiPluginService;
import io.yak.ops.common.enums.PluginType;
import io.yak.ops.dao.entity.DsVersion;
import io.yak.ops.dao.entity.PluginDefine;
import io.yak.ops.dao.mapper.PluginDefineMapper;
import io.yak.ops.dao.repository.DsVersionDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UiPluginServiceImpl extends BaseServiceImpl implements UiPluginService {

    @Autowired
    PluginDefineMapper pluginDefineMapper;

    @Autowired
    private DsVersionDao dsVersionDao;

    private String dsVersion;

    @PostConstruct
    private void init() {
        dsVersion = dsVersionDao.selectVersion().map(DsVersion::getVersion).orElse("unknown");
    }

    @Override
    public List<PluginDefine> queryUiPluginsByType(PluginType pluginType) {
        if (!pluginType.getHasUi()) {
            log.warn("Plugin does not have UI.");
            throw new ServiceException(Status.PLUGIN_NOT_A_UI_COMPONENT);
        }
        List<PluginDefine> pluginDefines = pluginDefineMapper.queryByPluginType(pluginType.getDesc());

        if (CollectionUtils.isEmpty(pluginDefines)) {
            log.warn("Query plugins result is null, check status of plugins.");
            throw new ServiceException(Status.QUERY_PLUGINS_RESULT_IS_NULL);
        }
        return pluginDefines;
    }

    @Override
    public PluginDefine queryUiPluginDetailById(int id) {
        PluginDefine pluginDefine = pluginDefineMapper.queryDetailById(id);
        if (null == pluginDefine) {
            log.warn("Query plugins result is empty, pluginId:{}.", id);
            throw new ServiceException(Status.QUERY_PLUGIN_DETAIL_RESULT_IS_NULL);
        }
        return pluginDefine;
    }

    @Override
    public ProductInfoDto queryProductInfo() {
        ProductInfoDto result = new ProductInfoDto();
        result.setVersion(dsVersion);
        return result;
    }

}
