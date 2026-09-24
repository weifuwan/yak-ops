package io.yak.ops.boot.project;

import io.yak.framework.security.common.vo.project.ProjectVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.service.ProjectService;
import io.yak.framework.security.service.UserService;
import io.yak.ops.core.project.ProjectAccessGuard;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Resolves Project Space access from Yak Security project membership. */
@Component
public class YakSecurityProjectAccessGuard implements ProjectAccessGuard {

  private final ProjectService projectService;
  private final UserService userService;

  public YakSecurityProjectAccessGuard(
      ProjectService projectService,
      UserService userService) {
    this.projectService = projectService;
    this.userService = userService;
  }

  @Override
  public ProjectContext requireAccessible(Long projectId, String username) {
    if (projectId == null || projectId <= 0 || !StringUtils.hasText(username)) {
      throw hiddenNotFound();
    }

    if (!projectService.checkProjectExist(projectId)) {
      throw hiddenNotFound();
    }

    UserBriefVO user = userService.getUserBriefByUsername(username);
    if (user == null || user.getId() == null) {
      throw hiddenNotFound();
    }

    ProjectVO project = projectService.getProjectDetailByProjectId(projectId);
    if (project == null || project.getId() == null) {
      throw hiddenNotFound();
    }

    if (!hasAccess(project, user.getId())) {
      throw hiddenNotFound();
    }

    if (!Boolean.TRUE.equals(project.getRunning())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_UNAVAILABLE);
    }

    return new ProjectContext(project.getId(), project.getProjectName());
  }

  private ProjectContextException hiddenNotFound() {
    return new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
  }

  private boolean hasAccess(ProjectVO project, Long userId) {
    return containsUser(project.getOwnerList(), userId)
        || containsUser(project.getUserList(), userId);
  }

  private boolean containsUser(List<UserBriefVO> users, Long userId) {
    List<UserBriefVO> safeUsers = users == null ? Collections.emptyList() : users;
    return safeUsers.stream().anyMatch(user -> Objects.equals(userId, user.getId()));
  }
}
