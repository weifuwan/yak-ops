package io.yak.ops.boot.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.ops.core.project.ProjectAccessGuard;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import io.yak.ops.core.project.ProjectHeaders;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class ProjectScopeInterceptorTest {

  private ProjectContextRuntime currentProject;
  private ProjectAccessGuard accessGuard;
  private CurrentUserProvider currentUserProvider;
  private ProjectScopeInterceptor interceptor;

  @BeforeEach
  void setUp() {
    currentProject = new ProjectContextRuntime();
    accessGuard = mock(ProjectAccessGuard.class);
    currentUserProvider = mock(CurrentUserProvider.class);
    interceptor = new ProjectScopeInterceptor(currentProject, accessGuard, currentUserProvider);
  }

  @Test
  void keepsLegacyEndpointsGlobalEvenWhenHeaderExists() throws Exception {
    MockHttpServletRequest request = requestWithProject("7");

    interceptor.preHandle(request, new MockHttpServletResponse(), handler("legacy"));

    assertFalse(currentProject.isPresent());
    verifyNoInteractions(accessGuard, currentUserProvider);
  }

  @Test
  void optionalEndpointAcceptsMissingProjectDuringBackfill() throws Exception {
    interceptor.preHandle(
        new MockHttpServletRequest(), new MockHttpServletResponse(), handler("optional"));

    assertFalse(currentProject.isPresent());
    verifyNoInteractions(accessGuard, currentUserProvider);
  }

  @Test
  void requiredEndpointRejectsMissingProjectForAuthenticatedUser() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    when(currentUserProvider.getCurrentUser(request)).thenReturn("alice");

    ProjectContextException error =
        assertThrows(
            ProjectContextException.class,
            () ->
                interceptor.preHandle(
                    request, new MockHttpServletResponse(), handler("required")));

    assertEquals(ProjectContextError.PROJECT_REQUIRED, error.getError());
    verifyNoInteractions(accessGuard);
  }

  @Test
  void invalidProjectHeaderReturnsNotFound() throws Exception {
    MockHttpServletRequest request = requestWithProject("not-a-project-id");
    when(currentUserProvider.getCurrentUser(request)).thenReturn("alice");

    ProjectContextException error =
        assertThrows(
            ProjectContextException.class,
            () ->
                interceptor.preHandle(
                    request, new MockHttpServletResponse(), handler("required")));

    assertEquals(ProjectContextError.PROJECT_NOT_FOUND, error.getError());
    verifyNoInteractions(accessGuard);
  }

  @Test
  void anonymousRequiredRequestDefersToAuthenticationBoundary() throws Exception {
    MockHttpServletRequest request = requestWithProject("7");

    interceptor.preHandle(request, new MockHttpServletResponse(), handler("required"));

    assertFalse(currentProject.isPresent());
    verifyNoInteractions(accessGuard);
  }

  @Test
  void bindsAuthorizedProjectAndClearsContextAfterCompletion() throws Exception {
    MockHttpServletRequest request = requestWithProject("7");
    when(currentUserProvider.getCurrentUser(request)).thenReturn("alice");
    when(accessGuard.requireAccessible(7L, "alice"))
        .thenReturn(new ProjectContext(7L, "Project A"));

    interceptor.preHandle(request, new MockHttpServletResponse(), handler("required"));
    assertEquals(7L, currentProject.requireProjectId());

    interceptor.afterCompletion(
        request, new MockHttpServletResponse(), handler("required"), null);

    assertFalse(currentProject.isPresent());
  }

  private MockHttpServletRequest requestWithProject(String projectId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ProjectHeaders.PROJECT_ID, projectId);
    return request;
  }

  private HandlerMethod handler(String methodName) throws Exception {
    Method method = TestController.class.getDeclaredMethod(methodName);
    return new HandlerMethod(new TestController(), method);
  }

  private static class TestController {

    void legacy() {}

    @ProjectScope(ProjectMigrationMode.PROJECT_OPTIONAL)
    void optional() {}

    @ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
    void required() {}
  }
}
