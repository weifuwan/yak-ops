package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.TaskGroup;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * the Dao interfaces of task group
 *
 * @author yinrui
 * @since 2021-08-07
 */
public interface TaskGroupMapper extends BaseMapper<TaskGroup> {

    /**
     * select task groups paging
     *
     * @param page   page
     * @param name   name
     * @param status status
     * @return result page
     */
    IPage<TaskGroup> queryTaskGroupPaging(IPage<TaskGroup> page,
                                          @Param("name") String name,
                                          @Param("status") Integer status);

    /**
     * query by task group name
     *
     * @param userId user id
     * @param name   name
     * @return task group
     */
    TaskGroup queryByName(@Param("userId") int userId, @Param("name") String name);

    IPage<TaskGroup> queryTaskGroupPagingByProjectCode(Page<TaskGroup> page, @Param("projectCode") Long projectCode);

    /**
     * listAuthorizedResource
     *
     * @param userId
     * @return
     */
    List<TaskGroup> listAuthorizedResource(@Param("userId") int userId);

    List<TaskGroup> selectByProjectCode(@Param("projectCode") long projectCode);

    List<TaskGroup> queryAvailableTaskGroups();

    List<TaskGroup> queryUsedTaskGroups();

    int acquireTaskGroupSlot(@Param("id") Integer id);

    int releaseTaskGroupSlot(@Param("id") Integer id);
}
