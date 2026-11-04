package io.yak.ops.api.service;

import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.dao.entity.AlertGroup;
import io.yak.ops.dao.entity.User;

import java.util.List;

public interface AlertGroupService {

    /**
     * query alert group list
     *
     * @param loginUser
     * @return alert group list
     */
    List<AlertGroup> queryAllAlertGroup(User loginUser);

    /**
     * query alert group by id
     *
     * @param loginUser login user
     * @param id alert group id
     * @return one alert group
     */
    AlertGroup queryAlertGroupById(User loginUser, Integer id);

    /**
     * paging query alarm group list
     *
     * @param loginUser login user
     * @param searchVal search value
     * @param pageNo page number
     * @param pageSize page size
     * @return alert group list page
     */
    PageInfo<AlertGroup> listPaging(User loginUser, String searchVal, Integer pageNo, Integer pageSize);

    /**
     * create alert group
     *
     * @param loginUser login user
     * @param groupName group name
     * @param desc description
     * @param alertInstanceIds alertInstanceIds
     * @return alertGroup
     */
    AlertGroup createAlertGroup(User loginUser, String groupName, String desc, String alertInstanceIds);

    /**
     * updateWorkflowInstance alert group
     *
     * @param loginUser login user
     * @param id alert group id
     * @param groupName group name
     * @param desc description
     * @param alertInstanceIds alertInstanceIds
     * @return update result code
     */
    AlertGroup updateAlertGroupById(User loginUser, int id, String groupName, String desc, String alertInstanceIds);

    /**
     * delete alert group by id
     *
     * @param loginUser login user
     * @param id alert group id
     * @return delete result code
     */
    void deleteAlertGroupById(User loginUser, int id);

    /**
     * verify group name exists
     *
     * @param groupName group name
     * @return check result code
     */
    boolean existGroupName(String groupName);
}
