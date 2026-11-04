package io.yak.ops.api.service;

import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.vo.AlertPluginInstanceVO;
import io.yak.ops.dao.entity.AlertPluginInstance;
import io.yak.ops.dao.entity.User;

import java.util.List;

public interface AlertPluginInstanceService {

    /**
     * creat alert plugin instance
     *
     * @param loginUser login user
     * @param pluginDefineId plugin define id
     * @param instanceName instance name
     * @param pluginInstanceParams plugin instance params
     * @return result
     */
    AlertPluginInstance create(User loginUser,
                               int pluginDefineId,
                               String instanceName,
                               String pluginInstanceParams);

    /**
     * update
     * @param loginUser login user
     * @param alertPluginInstanceId plugin instance id
     * @param instanceName instance name
     * @param pluginInstanceParams plugin instance params
     * @return result
     */
    AlertPluginInstance updateById(User loginUser,
                                   int alertPluginInstanceId,
                                   String instanceName,
                                   String pluginInstanceParams);

    /**
     * delete alert plugin instance
     *
     * @param loginUser login user
     * @param alertPluginInstanceId id
     * @return result
     */
    void deleteById(User loginUser, int alertPluginInstanceId);

    /**
     * get alert plugin instance
     *
     * @param loginUser login user
     * @param id get id
     * @return alert plugin
     */
    AlertPluginInstance getById(User loginUser, int id);

    /**
     * queryAll
     *
     * @return alert plugins
     */
    List<AlertPluginInstanceVO> queryAll(User loginUser);

    /**
     * checkExistPluginInstanceName
     * @param pluginName plugin name
     * @return isExist
     */
    boolean checkExistPluginInstanceName(User loginUser, String pluginName);

    /**
     * queryPluginPage
     * @param loginUser login user
     * @param searchVal search value
     * @param pageNo    page index
     * @param pageSize  page size
     * @return plugins
     */
    PageInfo<AlertPluginInstanceVO> listPaging(User loginUser, String searchVal, int pageNo, int pageSize);

    void testSend(User loginUser, int pluginDefineId, String pluginInstanceParams);
}
