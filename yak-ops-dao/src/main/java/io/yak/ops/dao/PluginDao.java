package io.yak.ops.dao;

import static java.util.Objects.requireNonNull;

import io.yak.ops.dao.entity.PluginDefine;
import io.yak.ops.dao.mapper.PluginDefineMapper;
import io.yak.ops.plugin.task.api.TaskPluginException;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class PluginDao {

    @Autowired
    private PluginDefineMapper pluginDefineMapper;

    /**
     * add or update plugin define
     *
     * @param pluginDefine new pluginDefine
     * @return plugin id
     */
    public int addOrUpdatePluginDefine(@NonNull PluginDefine pluginDefine) {
        requireNonNull(pluginDefine.getPluginName(), "pluginName is null");
        requireNonNull(pluginDefine.getPluginType(), "pluginType is null");

        PluginDefine currPluginDefine =
                pluginDefineMapper.queryByNameAndType(pluginDefine.getPluginName(), pluginDefine.getPluginType());
        if (currPluginDefine == null) {
            try {
                if (pluginDefineMapper.insert(pluginDefine) == 1 && pluginDefine.getId() != null) {
                    return pluginDefine.getId();
                }
                throw new TaskPluginException(
                        String.format("Failed to insert plugin definition, pluginName: %s, pluginType: %s",
                                pluginDefine.getPluginName(), pluginDefine.getPluginType()));
            } catch (TaskPluginException ex) {
                throw ex;
            } catch (Exception ex) {
                log.error("Insert plugin definition error, there may already exist a plugin", ex);
                currPluginDefine = pluginDefineMapper.queryByNameAndType(pluginDefine.getPluginName(),
                        pluginDefine.getPluginType());
                if (currPluginDefine == null) {
                    throw new TaskPluginException(
                            String.format("Failed to insert plugin definition, pluginName: %s, pluginType: %s",
                                    pluginDefine.getPluginName(), pluginDefine.getPluginType()));
                }
            }
        }
        if (!Objects.equals(currPluginDefine.getPluginParams(), pluginDefine.getPluginParams())) {
            currPluginDefine.setUpdateTime(pluginDefine.getUpdateTime());
            currPluginDefine.setPluginParams(pluginDefine.getPluginParams());
            pluginDefineMapper.updateById(currPluginDefine);
        }
        return currPluginDefine.getId();
    }

}
