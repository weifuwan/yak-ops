package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.EnvironmentWorkerGroupRelation;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface EnvironmentWorkerGroupRelationMapper extends BaseMapper<EnvironmentWorkerGroupRelation> {

    /**
     * environment worker group relation by environmentCode
     *
     * @param environmentCode environmentCode
     * @return EnvironmentWorkerGroupRelation list
     */
    List<EnvironmentWorkerGroupRelation> queryByEnvironmentCode(@Param("environmentCode") Long environmentCode);

    /**
     * environment worker group relation by workerGroupName
     *
     * @param workerGroupName workerGroupName
     * @return EnvironmentWorkerGroupRelation list
     */
    List<EnvironmentWorkerGroupRelation> queryByWorkerGroupName(@Param("workerGroupName") String workerGroupName);

    /**
     * delete environment worker group relation by processCode
     *
     * @param environmentCode environmentCode
     * @param workerGroupName workerGroupName
     * @return int
     */
    int deleteByCode(@Param("environmentCode") Long environmentCode, @Param("workerGroupName") String workerGroupName);
}
