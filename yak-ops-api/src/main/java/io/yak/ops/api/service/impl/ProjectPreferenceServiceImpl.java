package io.yak.ops.api.service.impl;

import static io.yak.ops.api.constants.ApiFuncIdentificationConstant.PROJECT;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.service.ProjectPreferenceService;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.utils.CodeGenerateUtils;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.ProjectPreference;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.mapper.ProjectPreferenceMapper;
import io.yak.ops.dao.repository.ProjectDao;

import java.util.Date;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProjectPreferenceServiceImpl extends BaseServiceImpl
        implements
            ProjectPreferenceService {

    @Autowired
    private ProjectPreferenceMapper projectPreferenceMapper;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectDao projectDao;

    @Override
    public Result updateProjectPreference(User loginUser, long projectCode, String preferences) {
        Result result = new Result();

        // check if the user has the writing permission for project
        Project project = projectDao.queryByCode(projectCode);
        projectService.checkHasProjectWritePermissionThrowException(loginUser, project);

        ProjectPreference projectPreference = projectPreferenceMapper
                .selectOne(new QueryWrapper<ProjectPreference>().lambda().eq(ProjectPreference::getProjectCode,
                        projectCode));

        Date now = new Date();
        if (Objects.isNull(projectPreference)) {
            projectPreference = new ProjectPreference();
            projectPreference.setProjectCode(projectCode);
            projectPreference.setPreferences(preferences);
            projectPreference.setUserId(loginUser.getId());
            projectPreference.setCode(CodeGenerateUtils.genCode());
            projectPreference.setState(1);
            projectPreference.setCreateTime(now);
            projectPreference.setUpdateTime(now);
            if (projectPreferenceMapper.insert(projectPreference) > 0) {
                log.info("Project preference is created and id is :{}", projectPreference.getId());
                result.setData(projectPreference);
                putMsg(result, Status.SUCCESS);
            } else {
                log.error("Project preference create error, projectCode:{}.", projectPreference.getProjectCode());
                putMsg(result, Status.CREATE_PROJECT_PREFERENCE_ERROR);
            }
        } else {
            projectPreference.setPreferences(preferences);
            projectPreference.setUserId(loginUser.getId());
            projectPreference.setUpdateTime(now);

            if (projectPreferenceMapper.updateById(projectPreference) > 0) {
                log.info("Project preference is updated and id is :{}", projectPreference.getId());
                result.setData(projectPreference);
                putMsg(result, Status.SUCCESS);
            } else {
                log.error("Project preference update error, projectCode:{}.", projectPreference.getProjectCode());
                putMsg(result, Status.UPDATE_PROJECT_PREFERENCE_ERROR);
            }
        }
        return result;
    }

    @Override
    public Result queryProjectPreferenceByProjectCode(User loginUser, long projectCode) {
        Result result = new Result();

        Project project = projectDao.queryByCode(projectCode);
        projectService.checkProjectAndAuthThrowException(loginUser, project, PROJECT);

        ProjectPreference projectPreference = projectPreferenceMapper
                .selectOne(new QueryWrapper<ProjectPreference>().lambda().eq(ProjectPreference::getProjectCode,
                        projectCode));

        result.setData(projectPreference);

        putMsg(result, Status.SUCCESS);
        return result;
    }

    @Override
    public Result enableProjectPreference(User loginUser, long projectCode, int state) {
        Result result = new Result();

        // check if the user has the writing permission for project
        Project project = projectDao.queryByCode(projectCode);
        projectService.checkHasProjectWritePermissionThrowException(loginUser, project);

        ProjectPreference projectPreference = projectPreferenceMapper
                .selectOne(new QueryWrapper<ProjectPreference>().lambda().eq(ProjectPreference::getProjectCode,
                        projectCode));

        putMsg(result, Status.SUCCESS);
        if (Objects.nonNull(projectPreference) && projectPreference.getState() != state) {
            projectPreference.setState(state);
            projectPreference.setUpdateTime(new Date());

            if (projectPreferenceMapper.updateById(projectPreference) > 0) {
                log.info("The state of the project preference is updated and id is :{}", projectPreference.getId());
            } else {
                log.error("Failed to update the state of the project preference, projectCode:{}.",
                        projectPreference.getProjectCode());
                putMsg(result, Status.UPDATE_PROJECT_PREFERENCE_STATE_ERROR);
            }
        }
        return result;
    }
}
