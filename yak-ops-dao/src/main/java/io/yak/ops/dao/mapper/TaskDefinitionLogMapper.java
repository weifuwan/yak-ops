package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskDefinitionLog;

import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface TaskDefinitionLogMapper extends BaseMapper<TaskDefinitionLog> {

    /**
     * query task definition log
     *
     * @param code taskDefinitionCode
     * @param version version
     * @return task definition log
     */
    TaskDefinitionLog queryByDefinitionCodeAndVersion(@Param("code") long code, @Param("version") int version);

    /**
     * update
     */
    int updateById(@Param("et") TaskDefinitionLog taskDefinitionLog);

    /**
     * delete the certain task definition version by task definition code and version
     *
     * @param code task definition code
     * @param version task definition version
     * @return delete result
     */
    int deleteByCodeAndVersion(@Param("code") long code, @Param("version") int version);

    /**
     * query max version for definition
     *
     * @param code taskDefinitionCode
     */
    Integer queryMaxVersionForDefinition(@Param("code") long code);

    /**
     * todo: rename to query by code and version
     * @param taskDefinitions taskDefinition list
     * @return list
     */
    List<TaskDefinitionLog> queryByTaskDefinitions(@Param("taskDefinitions") Collection<TaskDefinition> taskDefinitions);

    /**
     * batch insert task definition logs
     *
     * @param taskDefinitionLogs taskDefinitionLogs
     * @return int
     */
    int batchInsert(@Param("taskDefinitionLogs") List<TaskDefinitionLog> taskDefinitionLogs);

    /**
     * query the paging task definition version list by pagination info
     *
     * @param page pagination info
     * @param projectCode project code
     * @param code process definition code
     * @return the paging task definition version list
     */
    IPage<TaskDefinitionLog> queryTaskDefinitionVersionsPaging(Page<TaskDefinitionLog> page, @Param("code") long code,
                                                               @Param("projectCode") long projectCode);

    void deleteByTaskDefinitionCodes(@Param("taskDefinitionCodes") Set<Long> taskDefinitionCodes);
}
