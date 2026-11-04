package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.ProjectWorkerGroup;
import io.yak.ops.dao.mapper.ProjectWorkerGroupMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.ProjectWorkerGroupDao;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Repository;

import lombok.NonNull;

@Repository
public class ProjectWorkerGroupDaoImpl extends BaseDao<ProjectWorkerGroup, ProjectWorkerGroupMapper>
        implements
            ProjectWorkerGroupDao {

    public ProjectWorkerGroupDaoImpl(@NonNull ProjectWorkerGroupMapper projectWorkerGroupMapper) {
        super(projectWorkerGroupMapper);
    }

    @Override
    public boolean deleteByProjectCode(Long projectCode) {
        int deleted = mybatisMapper.deleteByProjectCode(projectCode);
        return deleted > 0;
    }

    @Override
    public Set<String> queryAssignedWorkerGroupNamesByProjectCode(Long projectCode) {
        if (projectCode == null) {
            return null;
        }
        return mybatisMapper.queryAssignedWorkerGroupNamesByProjectCode(projectCode);
    }

    @Override
    public boolean deleteByProjectCodeAndWorkerGroups(Long projectCode, List<String> workerGroups) {
        if (mybatisMapper.queryAssignedWorkerGroupNamesByProjectCode(projectCode).isEmpty()) {
            return true;
        }
        int deleted = mybatisMapper.deleteByProjectCodeAndWorkerGroups(projectCode, workerGroups);
        return deleted > 0;
    }

    @Override
    public List<ProjectWorkerGroup> queryByProjectCode(Long projectCode) {
        return mybatisMapper.queryByProjectCode(projectCode);
    }
}
