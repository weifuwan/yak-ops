package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.ProjectUser;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface ProjectUserMapper extends BaseMapper<ProjectUser> {

    /**
     * delete project user relation
     *
     * @param projectId projectId
     * @param userId userId
     * @return delete result
     */
    int deleteProjectRelation(@Param("projectId") int projectId,
                              @Param("userId") int userId);

    /**
     * query project relation
     *
     * @param projectId projectId
     * @param userId userId
     * @return project user relation
     */
    ProjectUser queryProjectRelation(@Param("projectId") int projectId,
                                     @Param("userId") int userId);
}
