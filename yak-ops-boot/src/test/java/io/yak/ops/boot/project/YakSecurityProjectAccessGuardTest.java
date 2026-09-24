package io.yak.ops.boot.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.framework.security.common.vo.project.ProjectVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.service.ProjectService;
import io.yak.framework.security.service.UserService;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YakSecurityProjectAccessGuardTest {

  private ProjectService projectService;
  private UserService userService;
  private YakSecurityProjectAccessGuard guard;

  @BeforeEach
  void setUp() {
    projectService = mock(ProjectService.class);
    userService = mock(UserService.class);
    guard = new YakSecurityProjectAccessGuard(projectService, userService);
  }

  @Test
  void ownerAccessIsAllowed() {
    UserBriefVO user = user(11L);
    ProjectVO project = project(7L, "Project A", true, List.of(user), List.of());
    stubExistingProject(user, project);

    ProjectContext context = guard.requireAccessible(7L, "alice");

    assertThat(context.projectId()).isEqualTo(7L);
    assertThat(context.projectName()).isEqualTo("Project A");
  }

  @Test
  void memberAccessIsAllowed() {
    UserBriefVO user = user(11L);
    ProjectVO project = project(7L, "Project A", true, List.of(), List.of(user));
    stubExistingProject(user, project);

    ProjectContext context = guard.requireAccessible(7L, "alice");

    assertThat(context.projectId()).isEqualTo(7L);
  }

  @Test
  void membershipDenialKeepsOutwardNotFound() {
    UserBriefVO user = user(11L);
    ProjectVO project = project(7L, "Project A", true, List.of(), List.of());
    stubExistingProject(user, project);

    assertThatThrownBy(() -> guard.requireAccessible(7L, "alice"))
        .isInstanceOfSatisfying(
            ProjectContextException.class,
            exception ->
                assertThat(exception.getError()).isEqualTo(ProjectContextError.PROJECT_NOT_FOUND));
  }

  @Test
  void missingProjectKeepsOutwardNotFound() {
    when(projectService.checkProjectExist(7L)).thenReturn(false);

    assertThatThrownBy(() -> guard.requireAccessible(7L, "alice"))
        .isInstanceOfSatisfying(
            ProjectContextException.class,
            exception ->
                assertThat(exception.getError()).isEqualTo(ProjectContextError.PROJECT_NOT_FOUND));
  }

  @Test
  void unavailableProjectPreservesExistingOutwardError() {
    UserBriefVO user = user(11L);
    ProjectVO project = project(7L, "Project A", false, List.of(user), List.of());
    stubExistingProject(user, project);

    assertThatThrownBy(() -> guard.requireAccessible(7L, "alice"))
        .isInstanceOfSatisfying(
            ProjectContextException.class,
            exception ->
                assertThat(exception.getError()).isEqualTo(ProjectContextError.PROJECT_UNAVAILABLE));
  }

  private void stubExistingProject(UserBriefVO user, ProjectVO project) {
    when(projectService.checkProjectExist(7L)).thenReturn(true);
    when(userService.getUserBriefByUsername("alice")).thenReturn(user);
    when(projectService.getProjectDetailByProjectId(7L)).thenReturn(project);
  }

  private UserBriefVO user(long id) {
    UserBriefVO user = mock(UserBriefVO.class);
    when(user.getId()).thenReturn(id);
    return user;
  }

  private ProjectVO project(
      long id,
      String name,
      boolean running,
      List<UserBriefVO> owners,
      List<UserBriefVO> members) {
    ProjectVO project = mock(ProjectVO.class);
    when(project.getId()).thenReturn(id);
    when(project.getProjectName()).thenReturn(name);
    when(project.getRunning()).thenReturn(running);
    when(project.getOwnerList()).thenReturn(owners);
    when(project.getUserList()).thenReturn(members);
    return project;
  }
}
