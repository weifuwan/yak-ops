package io.yak.ops.api.service;

import io.yak.ops.api.dto.ProductInfoDto;
import io.yak.ops.common.enums.PluginType;
import io.yak.ops.dao.entity.PluginDefine;

import java.util.List;

public interface UiPluginService {

    List<PluginDefine> queryUiPluginsByType(PluginType pluginType);

    PluginDefine queryUiPluginDetailById(int id);

    ProductInfoDto queryProductInfo();

}
