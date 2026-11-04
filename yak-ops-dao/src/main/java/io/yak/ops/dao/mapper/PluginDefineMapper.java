package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.PluginDefine;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface PluginDefineMapper extends BaseMapper<PluginDefine> {

    /**
     * query all plugin define
     *
     * @return PluginDefine list
     */
    List<PluginDefine> queryAllPluginDefineList();

    /**
     * query by plugin type
     *
     * @param pluginType pluginType
     * @return PluginDefine list
     */
    List<PluginDefine> queryByPluginType(@Param("pluginType") String pluginType);

    /**
     * query detail by id
     *
     * @param id id
     * @return PluginDefineDetail
     */
    PluginDefine queryDetailById(@Param("id") int id);

    /**
     * query by name and type
     *
     * @param pluginName
     * @param pluginType
     * @return
     */
    PluginDefine queryByNameAndType(@Param("pluginName") String pluginName, @Param("pluginType") String pluginType);
}
