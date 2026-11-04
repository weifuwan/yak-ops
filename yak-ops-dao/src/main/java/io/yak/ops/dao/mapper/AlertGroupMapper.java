package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.AlertGroup;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * alertgroup mapper interface
 */
public interface AlertGroupMapper extends BaseMapper<AlertGroup> {

    /**
     * alertgroup page
     * @param page page
     * @param groupName groupName
     * @return alertgroup Ipage
     */
    IPage<AlertGroup> queryAlertGroupPage(Page page,
                                          @Param("groupName") String groupName);

    /**
     * query by group name
     * @param groupName groupName
     * @return alertgroup list
     */
    List<AlertGroup> queryByGroupName(@Param("groupName") String groupName);

    /**
     * Judge whether the alert group exist
     * @param groupName groupName
     * @return if exist return true else return null
     */
    Boolean existGroupName(@Param("groupName") String groupName);

    /**
     * query by userId
     * @param userId userId
     * @return alertgroup list
     */
    List<AlertGroup> queryByUserId(@Param("userId") int userId);

    /**
     * query all group list
     * @return alertgroup list
     */
    List<AlertGroup> queryAllGroupList();

    /**
     * query instance ids All
     * @return list
     */
    List<String> queryInstanceIdsList();

    /**
     * queryAlertGroupInstanceIdsById
     * @param alertGroupId
     * @return
     */
    String queryAlertGroupInstanceIdsById(@Param("alertGroupId") int alertGroupId);

    /**
     * list authorized AlertGroup
     * @param userId
     * @param alertGroupsIds
     * @return
     */
    <T> List<AlertGroup> listAuthorizedAlertGroupList(@Param("userId") int userId,
                                                      @Param("alertGroupsIds") List<Integer> alertGroupsIds);

    /**
     * queryAlertGroupPageByIds
     * @param page
     * @param ids
     * @param searchVal
     * @return
     */
    IPage<AlertGroup> queryAlertGroupPageByIds(Page<AlertGroup> page, @Param("ids") List<Integer> ids,
                                               @Param("searchVal") String searchVal);
}
