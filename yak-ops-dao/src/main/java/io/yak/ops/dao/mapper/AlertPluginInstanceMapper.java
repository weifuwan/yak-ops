package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.AlertPluginInstance;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface AlertPluginInstanceMapper extends BaseMapper<AlertPluginInstance> {

    /**
     * query all alert plugin instance
     *
     * @return AlertPluginInstance list
     */
    List<AlertPluginInstance> queryAllAlertPluginInstanceList();

    /**
     * query by alert group id
     *
     * @param ids
     * @return AlertPluginInstance list
     */
    List<AlertPluginInstance> queryByIds(@Param("ids") List<Integer> ids);

    /**
     * Query alert plugin instance by given name
     * @param page                page
     * @param instanceName         Alert plugin name
     * @return alertPluginInstance Ipage
     */
    IPage<AlertPluginInstance> queryByInstanceNamePage(Page page, @Param("instanceName") String instanceName);

    /**
     *
     * @param instanceName instanceName
     * @return if exist return true else return null
     */
    Boolean existInstanceName(@Param("instanceName") String instanceName);
}
