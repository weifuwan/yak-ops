package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.ProjectWorkerGroup;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface ProjectWorkerGroupMapper extends BaseMapper<ProjectWorkerGroup> {

    int deleteByProjectCode(@Param("projectCode") Long projectCode);

    Set<String> queryAssignedWorkerGroupNamesByProjectCode(@Param("projectCode") Long projectCode);

    int deleteByProjectCodeAndWorkerGroups(@Param("projectCode") Long projectCode,
                                           @Param("workerGroups") List<String> workerGroups);

    List<ProjectWorkerGroup> queryByProjectCode(@Param("projectCode") Long projectCode);
}
