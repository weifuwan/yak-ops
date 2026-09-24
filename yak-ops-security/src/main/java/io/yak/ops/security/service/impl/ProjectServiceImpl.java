package io.yak.ops.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.ops.security.common.dto.oplog.OplogDTO;
import io.yak.ops.security.common.dto.project.ProjectBriefQueryDTO;
import io.yak.ops.security.common.dto.project.ProjectQueryDTO;
import io.yak.ops.security.common.dto.project.ProjectSaveDTO;
import io.yak.ops.security.common.dto.resource.ResourceDTO;
import io.yak.ops.security.common.entity.UserProject;
import io.yak.ops.security.common.entity.dept.Dept;
import io.yak.ops.security.common.entity.project.Project;
import io.yak.ops.security.common.entity.project.ProjectBrief;
import io.yak.ops.security.common.enums.ResultCode;
import io.yak.ops.security.common.enums.project.ProjectUserCode;
import io.yak.ops.security.common.vo.project.ProjectBriefVO;
import io.yak.ops.security.common.vo.project.ProjectBriefVOWithUser;
import io.yak.ops.security.common.vo.project.ProjectDeleteCheckVO;
import io.yak.ops.security.common.vo.project.ProjectVO;
import io.yak.ops.security.common.vo.user.UserBasicVO;
import io.yak.ops.security.common.vo.user.UserBriefVO;
import io.yak.ops.security.dao.ProjectDao;
import io.yak.ops.security.exception.YakSecurityException;
import io.yak.ops.security.extend.ResourceExtend;
import io.yak.ops.security.service.DeptService;
import io.yak.ops.security.service.OplogService;
import io.yak.ops.security.service.ProjectService;
import io.yak.ops.security.service.UserProjectService;
import io.yak.ops.security.service.UserService;
import io.yak.ops.security.util.CopyBeanUtil;
import io.yak.ops.security.util.JsonUtils;
import io.yak.ops.security.util.MathUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 项目服务实现类。
 *
 * @author weifuwan
 */
@Service("yakSecurityProjectServiceImpl")
public class ProjectServiceImpl
        implements ProjectService {

  private static final String PROJECT_CODE_PREFIX = "p";

  private static final int PROJECT_CODE_RANDOM_LENGTH = 7;

  private static final String OPERATION_CREATE = "新增";

  private static final String OPERATION_EDIT = "编辑";

  private static final String OPERATION_DELETE = "删除";

  private static final String OPERATION_ENABLE = "启用";

  private static final String OPERATION_DISABLE = "停用";

  private static final String OPERATION_OBJECT_PROJECT =
          "Project";

  private final ProjectDao projectDao;

  private final DeptService deptService;

  private final OplogService oplogService;

  private final UserService userService;

  private final UserProjectService userProjectService;

  private final ResourceExtend resourceExtend;

  /**
   * 创建项目服务。
   *
   * @param projectDao 项目数据访问对象
   * @param deptService 部门服务
   * @param oplogService 操作日志服务
   * @param userService 用户服务
   * @param userProjectService 用户项目关系服务
   * @param resourceExtend 资源扩展
   */
  public ProjectServiceImpl(
          ProjectDao projectDao,
          DeptService deptService,
          OplogService oplogService,
          UserService userService,
          UserProjectService userProjectService,
          ResourceExtend resourceExtend) {

    this.projectDao = projectDao;
    this.deptService = deptService;
    this.oplogService = oplogService;
    this.userService = userService;
    this.userProjectService = userProjectService;
    this.resourceExtend = resourceExtend;
  }

  /**
   * 根据项目 ID 查询项目详情。
   *
   * @param projectId 项目 ID
   * @return 项目详情
   */
  @Override
  public ProjectVO getProjectDetailByProjectId(
          Long projectId) {

    Project project = getRequiredProject(projectId);

    ProjectVO projectVO =
            CopyBeanUtil.copy(
                    project,
                    ProjectVO.class);

    if (projectVO == null) {
      throw new IllegalStateException(
              "项目对象转换失败");
    }

    List<Long> userIdList =
            userProjectService
                    .getUserIdListByProjectId(
                            projectId,
                            ProjectUserCode.NORMAL);

    projectVO.setUserList(
            userService
                    .getUserBriefListByUserIds(
                            userIdList));

    List<Long> ownerIdList =
            userProjectService
                    .getUserIdListByProjectId(
                            projectId,
                            ProjectUserCode.OWNER);

    projectVO.setOwnerList(
            userService
                    .getUserBriefListByUserIds(
                            ownerIdList));

    projectVO.setDeptList(
            deptService
                    .getDeptBriefListByChildId(
                            project.getDeptId()));

    projectVO.setCreateTime(
            project.getCreateTime());

    return projectVO;
  }

  /**
   * 根据项目 ID 查询项目简要信息。
   *
   * @param projectId 项目 ID
   * @return 项目简要信息
   */
  @Override
  public ProjectBriefVO getProjectBriefByProjectId(
          Long projectId) {

    if (projectId == null) {
      return null;
    }

    Project project =
            projectDao.selectByProjectId(
                    projectId);

    return CopyBeanUtil.copy(
            project,
            ProjectBriefVO.class);
  }

  /**
   * 创建项目。
   *
   * @param projectSaveDTO 项目信息
   * @param operator 操作人
   * @return 创建后的项目详情
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public ProjectVO createProject(
          ProjectSaveDTO projectSaveDTO,
          String operator) {

    checkParam(projectSaveDTO, false);

    Project project =
            CopyBeanUtil.copy(
                    projectSaveDTO,
                    Project.class);

    if (project == null) {
      throw new IllegalStateException(
              "项目对象转换失败");
    }

    project.setProjectCode(
            generateProjectCode());

    projectDao.insert(project);

    userProjectService.saveOwnerProject(
            project.getId(),
            projectSaveDTO.getOwnerIdList());

    userProjectService.saveUserProject(
            project.getId(),
            projectSaveDTO.getUserIdList());

    oplogService.saveOplog(
            new OplogDTO(
                    operator,
                    OPERATION_CREATE,
                    OPERATION_OBJECT_PROJECT,
                    project.getProjectName(),
                    "'' -> "
                            + project.getProjectName()));

    return getProjectDetailByProjectId(
            project.getId());
  }

  /**
   * 分页查询项目。
   *
   * @param queryDTO 查询条件
   * @return 项目分页数据
   */
  @Override
  public PagingData<ProjectVO> getProjectPage(
          ProjectQueryDTO queryDTO) {

    return getProjectPageInternal(
            queryDTO,
            null);
  }

  /**
   * 分页查询项目。
   *
   * @param queryDTO 查询条件
   * @param projectIdList 额外项目 ID 列表
   * @return 项目分页数据
   */
  @Override
  public PagingData<ProjectVO> getProjectPage(
          ProjectQueryDTO queryDTO,
          List<Long> projectIdList) {

    return getProjectPageInternal(
            queryDTO,
            projectIdList);
  }

  /**
   * 根据项目 ID 删除项目。
   *
   * @param projectId 项目 ID
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void deleteProjectByProjectId(
          Long projectId,
          String operator) {

    Project project =
            getRequiredProject(projectId);

    List<String> resourceList =
            listResourceOfProject(
                    projectId);
    if (!CollectionUtils.isEmpty(resourceList)) {
      throw new YakSecurityException(
              ResultCode
                      .PROJECT_DEL_RESOURCE_NOT_NULL);
    }

    userProjectService
            .deleteUserProjectByProjectId(
                    projectId);

    userProjectService
            .deleteOwnerProjectByProjectId(
                    projectId);

    projectDao.deleteByProjectId(
            projectId);

    oplogService.saveOplog(
            new OplogDTO(
                    operator,
                    OPERATION_DELETE,
                    OPERATION_OBJECT_PROJECT,
                    project.getProjectName(),
                    project.getProjectName()
                            + " -> ''"));
  }

  /**
   * 更新项目。
   *
   * @param projectSaveDTO 项目信息
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void updateProject(
          ProjectSaveDTO projectSaveDTO,
          String operator) {

    if (projectSaveDTO == null
            || projectSaveDTO.getId() == null) {

      throw new IllegalArgumentException(
              "项目信息和项目 ID 不能为空");
    }

    getRequiredProject(
            projectSaveDTO.getId());

    checkParam(projectSaveDTO, true);

    Project project =
            CopyBeanUtil.copy(
                    projectSaveDTO,
                    Project.class);

    if (project == null) {
      throw new IllegalStateException(
              "项目对象转换失败");
    }

    projectDao.update(project);

    /*
     * null 表示本次请求不修改关系；
     * 空列表表示清空全部关系。
     */
    if (projectSaveDTO.getUserIdList() != null) {
      userProjectService.updateUserProject(
              projectSaveDTO.getId(),
              projectSaveDTO.getUserIdList());
    }

    if (projectSaveDTO.getOwnerIdList() != null) {
      userProjectService.updateOwnerProject(
              projectSaveDTO.getId(),
              projectSaveDTO.getOwnerIdList());
    }

    oplogService.saveOplog(
            new OplogDTO(
                    operator,
                    OPERATION_EDIT,
                    OPERATION_OBJECT_PROJECT,
                    projectSaveDTO.getProjectName(),
                    JsonUtils.toJson(
                            projectSaveDTO)));
  }

  /**
   * 变更项目状态。
   *
   * @param projectId 项目 ID
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void changeProjectStatus(
          Long projectId,
          String operator) {

    Project project =
            getRequiredProject(projectId);

    boolean newRunningStatus =
            !Boolean.TRUE.equals(
                    project.getRunning());

    project.setRunning(
            newRunningStatus);

    projectDao.update(project);

    oplogService.saveOplog(
            new OplogDTO(
                    operator,
                    newRunningStatus
                            ? OPERATION_ENABLE
                            : OPERATION_DISABLE,
                    OPERATION_OBJECT_PROJECT,
                    project.getProjectName(),
                    "status:"
                            + newRunningStatus));
  }

  /**
   * 添加项目用户。
   *
   * @param projectId 项目 ID
   * @param userId 用户 ID
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void addProjectUser(
          Long projectId,
          Long userId,
          String operator) {

    Project project =
            getRequiredProject(projectId);

    checkUserId(userId);

    /*
     * 只补充不存在的关系，避免重复插入。
     */
    userProjectService
            .updateUserInformationAssociatedWithProject(
                    projectId,
                    Collections.singletonList(
                            userId));

    saveRelationOplog(
            operator,
            OPERATION_CREATE,
            project,
            "增加项目用户：" + userId);
  }

  /**
   * 删除项目用户。
   *
   * @param projectId 项目 ID
   * @param userId 用户 ID
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void delProjectUser(
          Long projectId,
          Long userId,
          String operator) {

    Project project =
            getRequiredProject(projectId);

    checkUserId(userId);

    userProjectService.delUserProject(
            projectId,
            Collections.singletonList(
                    userId));

    saveRelationOplog(
            operator,
            OPERATION_DELETE,
            project,
            "删除项目用户：" + userId);
  }

  /**
   * 添加项目负责人。
   *
   * @param projectId 项目 ID
   * @param ownerId 负责人 ID
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void addProjectOwner(
          Long projectId,
          Long ownerId,
          String operator) {

    Project project =
            getRequiredProject(projectId);

    checkUserId(ownerId);

    userProjectService
            .updateOwnerInformationAssociatedWithProject(
                    projectId,
                    Collections.singletonList(
                            ownerId));

    saveRelationOplog(
            operator,
            OPERATION_CREATE,
            project,
            "增加项目负责人：" + ownerId);
  }

  /**
   * 删除项目负责人。
   *
   * @param projectId 项目 ID
   * @param ownerId 负责人 ID
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void delProjectOwner(
          Long projectId,
          Long ownerId,
          String operator) {

    Project project =
            getRequiredProject(projectId);

    checkUserId(ownerId);

    userProjectService.delOwnerProject(
            projectId,
            Collections.singletonList(
                    ownerId));

    saveRelationOplog(
            operator,
            OPERATION_DELETE,
            project,
            "删除项目负责人：" + ownerId);
  }

  /**
   * 查询全部项目简要信息。
   *
   * @return 项目简要信息列表
   */
  @Override
  public List<ProjectBriefVO> getProjectBriefList() {
    List<ProjectBriefVO> projectList =
            CopyBeanUtil.copyList(
                    projectDao.selectAllBriefList(),
                    ProjectBriefVO.class);

    return projectList == null
            ? new ArrayList<>()
            : projectList;
  }

  /**
   * 执行项目删除前校验。
   *
   * @param projectId 项目 ID
   * @return 删除校验结果
   */
  @Override
  public ProjectDeleteCheckVO checkBeforeDelete(
          Long projectId) {

    if (projectId == null) {
      return null;
    }

    return new ProjectDeleteCheckVO(
            projectId,
            listResourceOfProject(
                    projectId));
  }

  /**
   * 分页查询项目简要信息。
   *
   * @param queryDTO 查询条件
   * @return 项目简要分页数据
   */
  @Override
  public PagingData<ProjectBriefVO>
  getProjectBriefPage(
          ProjectBriefQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "项目查询条件不能为空");
    }

    IPage<ProjectBrief> projectPage =
            projectDao.selectBriefPage(
                    queryDTO);

    List<ProjectBriefVO> projectList =
            CopyBeanUtil.copyList(
                    projectPage.getRecords(),
                    ProjectBriefVO.class);

    if (projectList == null) {
      projectList = new ArrayList<>();
    }

    return toPagingData(
            projectList,
            projectPage);
  }

  /**
   * 校验项目是否存在。
   *
   * @param projectId 项目 ID
   * @return 项目是否存在
   */
  @Override
  public boolean checkProjectExist(
          Long projectId) {

    return projectId != null
            && projectDao.selectByProjectId(
            projectId) != null;
  }

  /**
   * 根据项目 ID 查询未分配用户。
   *
   * @param projectId 项目 ID
   * @return 未分配用户列表
   */
  @Override
  public Result<List<UserBriefVO>>
  unassignedByProjectId(
          Long projectId) {

    getRequiredProject(projectId);

    Set<Long> assignedUserIds =
            new HashSet<>();

    assignedUserIds.addAll(
            userProjectService
                    .getUserIdListByProjectId(
                            projectId,
                            ProjectUserCode.NORMAL));

    assignedUserIds.addAll(
            userProjectService
                    .getUserIdListByProjectId(
                            projectId,
                            ProjectUserCode.OWNER));

    List<UserBriefVO> allUserList =
            userService.getAllUserBriefList();

    if (CollectionUtils.isEmpty(allUserList)) {
      return Result.success(
              new ArrayList<>());
    }

    List<UserBriefVO> unassignedUserList =
            allUserList.stream()
                    .filter(Objects::nonNull)
                    .filter(user ->
                            user.getId() != null)
                    .filter(user ->
                            !assignedUserIds.contains(
                                    user.getId()))
                    .collect(Collectors.toList());

    return Result.success(
            unassignedUserList);
  }

  /**
   * 根据用户 ID 查询项目简要信息。
   *
   * @param userId 用户 ID
   * @return 项目简要信息列表
   */
  @Override
  public Result<List<ProjectBriefVO>>
  getProjectBriefByUserId(
          Long userId) {

    if (userId == null) {
      return Result.buildParamIllegal(
              "用户 ID 不能为空");
    }

    List<Long> projectIdList =
            userProjectService
                    .getProjectIdListByUserIdList(
                            Collections.singletonList(
                                    userId));

    if (CollectionUtils.isEmpty(projectIdList)) {
      return Result.success(
              new ArrayList<>());
    }

    List<ProjectBriefVO> projectList =
            projectIdList.stream()
                    .map(this::getProjectBriefByProjectId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

    return Result.success(projectList);
  }

  /**
   * 根据项目 ID 集合查询包含用户信息的项目。
   *
   * @param projectIdList 项目 ID 列表
   * @return 包含用户信息的项目列表
   */
  @Override
  public List<ProjectBriefVOWithUser>
  listProjectBriefVOWithUserByProjectIds(
          List<Long> projectIdList) {

    List<Long> validProjectIds =
            normalizeIds(projectIdList);

    if (validProjectIds.isEmpty()) {
      return new ArrayList<>();
    }

    List<Project> projectList =
            projectDao
                    .selectProjectBriefByProjectIds(
                            validProjectIds);

    if (CollectionUtils.isEmpty(projectList)) {
      return new ArrayList<>();
    }

    List<UserProject> userProjectList =
            userProjectService
                    .lisUserProjectByProjectIds(
                            validProjectIds);

    Map<Long, Map<Integer, Set<Long>>>
            relationMap =
            buildProjectUserRelationMap(
                    userProjectList);

    List<Long> userIdList =
            getAllRelationUserIds(
                    userProjectList);

    List<UserBasicVO> userList =
            userService
                    .getUserBasicListByUserIds(
                            userIdList);

    Map<Long, UserBasicVO> userMap =
            buildUserBasicMap(userList);

    List<ProjectBriefVOWithUser> resultList =
            new ArrayList<>(projectList.size());

    for (Project project : projectList) {
      ProjectBriefVOWithUser projectVO =
              CopyBeanUtil.copy(
                      project,
                      ProjectBriefVOWithUser.class);

      if (projectVO == null) {
        continue;
      }

      projectVO.setUserList(
              resolveUserBasicList(
                      project.getId(),
                      ProjectUserCode.NORMAL.getType(),
                      relationMap,
                      userMap));

      projectVO.setOwnerList(
              resolveUserBasicList(
                      project.getId(),
                      ProjectUserCode.OWNER.getType(),
                      relationMap,
                      userMap));

      resultList.add(projectVO);
    }

    return resultList;
  }

  /**
   * 执行项目分页查询。
   */
  private PagingData<ProjectVO>
  getProjectPageInternal(
          ProjectQueryDTO queryDTO,
          List<Long> additionalProjectIds) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "项目查询条件不能为空");
    }

    List<Long> projectIdFilter =
            resolveProjectIdFilter(
                    queryDTO,
                    additionalProjectIds);

    List<Long> deptIdFilter =
            queryDTO.getDeptId() == null
                    ? null
                    : deptService
                    .getDeptIdListByParentId(
                            queryDTO.getDeptId());

    IPage<Project> projectPage =
            projectDao
                    .selectPageByDeptIdListAndProjectIdList(
                            queryDTO,
                            deptIdFilter,
                            projectIdFilter);

    List<ProjectVO> projectList =
            buildProjectVOList(
                    projectPage.getRecords());

    return toPagingData(
            projectList,
            projectPage);
  }

  /**
   * 构建项目 ID 查询过滤条件。
   */
  private List<Long> resolveProjectIdFilter(
          ProjectQueryDTO queryDTO,
          List<Long> additionalProjectIds) {

    boolean hasChargeUser =
            StringUtils.hasText(
                    queryDTO.getChargeUsername());

    List<Long> validAdditionalIds =
            normalizeIds(
                    additionalProjectIds);

    if (!hasChargeUser
            && validAdditionalIds.isEmpty()) {

      /*
       * null 表示不使用项目 ID 条件。
       */
      return null;
    }

    Set<Long> projectIdSet =
            new LinkedHashSet<>();

    if (hasChargeUser) {
      List<Long> userIdList =
              userService.searchUserIds(
                      queryDTO
                              .getChargeUsername()
                              .trim());

      List<Long> matchedProjectIds =
              userProjectService
                      .getProjectIdListByUserIdList(
                              userIdList);

      projectIdSet.addAll(
              normalizeIds(
                      matchedProjectIds));
    }

    projectIdSet.addAll(
            validAdditionalIds);

    return new ArrayList<>(
            projectIdSet);
  }

  /**
   * 构建项目详情列表。
   */
  private List<ProjectVO> buildProjectVOList(
          List<Project> projectList) {

    if (CollectionUtils.isEmpty(projectList)) {
      return new ArrayList<>();
    }

    List<Long> projectIdList =
            projectList.stream()
                    .map(Project::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

    List<UserProject> userProjectList =
            userProjectService
                    .lisUserProjectByProjectIds(
                            projectIdList);

    Map<Long, Map<Integer, Set<Long>>>
            relationMap =
            buildProjectUserRelationMap(
                    userProjectList);

    List<Long> userIdList =
            getAllRelationUserIds(
                    userProjectList);

    List<UserBriefVO> userList =
            userService
                    .getUserBriefListByUserIds(
                            userIdList);

    Map<Long, UserBriefVO> userMap =
            buildUserBriefMap(userList);

    Map<Long, Dept> deptMap =
            deptService.getAllDeptMap();

    List<ProjectVO> resultList =
            new ArrayList<>(projectList.size());

    for (Project project : projectList) {
      ProjectVO projectVO =
              CopyBeanUtil.copy(
                      project,
                      ProjectVO.class);

      if (projectVO == null) {
        continue;
      }

      projectVO.setUserList(
              resolveUserBriefList(
                      project.getId(),
                      ProjectUserCode.NORMAL.getType(),
                      relationMap,
                      userMap));

      projectVO.setOwnerList(
              resolveUserBriefList(
                      project.getId(),
                      ProjectUserCode.OWNER.getType(),
                      relationMap,
                      userMap));

      projectVO.setDeptList(
              deptService
                      .getDeptBriefListFromDeptMapByChildId(
                              deptMap,
                              project.getDeptId()));

      projectVO.setCreateTime(
              project.getCreateTime());

      resultList.add(projectVO);
    }

    return resultList;
  }

  /**
   * 构建项目与用户关系映射。
   */
  private Map<Long, Map<Integer, Set<Long>>>
  buildProjectUserRelationMap(
          List<UserProject> userProjectList) {

    Map<Long, Map<Integer, Set<Long>>> result =
            new HashMap<>();

    if (CollectionUtils.isEmpty(
            userProjectList)) {

      return result;
    }

    for (UserProject relation
            : userProjectList) {

      if (relation == null
              || relation.getProjectId() == null
              || relation.getUserId() == null
              || relation.getUserType() == null) {

        continue;
      }

      result.computeIfAbsent(
              relation.getProjectId(),
              key -> new HashMap<>())
              .computeIfAbsent(
                      relation.getUserType(),
                      key -> new LinkedHashSet<>())
              .add(relation.getUserId());
    }

    return result;
  }

  /**
   * 获取全部关系用户 ID。
   */
  private List<Long> getAllRelationUserIds(
          List<UserProject> userProjectList) {

    if (CollectionUtils.isEmpty(
            userProjectList)) {

      return new ArrayList<>();
    }

    return userProjectList.stream()
            .filter(Objects::nonNull)
            .map(UserProject::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
  }

  /**
   * 获取项目指定类型的用户 ID。
   */
  private Set<Long> getProjectUserIds(
          Long projectId,
          Integer userType,
          Map<Long, Map<Integer, Set<Long>>>
                  relationMap) {

    Map<Integer, Set<Long>> userTypeMap =
            relationMap.get(projectId);

    if (userTypeMap == null) {
      return Collections.emptySet();
    }

    return userTypeMap.getOrDefault(
            userType,
            Collections.emptySet());
  }

  /**
   * 解析项目用户简要信息。
   */
  private List<UserBriefVO> resolveUserBriefList(
          Long projectId,
          Integer userType,
          Map<Long, Map<Integer, Set<Long>>>
                  relationMap,
          Map<Long, UserBriefVO> userMap) {

    Set<Long> userIds =
            getProjectUserIds(
                    projectId,
                    userType,
                    relationMap);

    List<UserBriefVO> result =
            new ArrayList<>(userIds.size());

    for (Long userId : userIds) {
      UserBriefVO user =
              userMap.get(userId);

      if (user != null) {
        result.add(user);
      }
    }

    return result;
  }

  /**
   * 解析项目用户基础信息。
   */
  private List<UserBasicVO> resolveUserBasicList(
          Long projectId,
          Integer userType,
          Map<Long, Map<Integer, Set<Long>>>
                  relationMap,
          Map<Long, UserBasicVO> userMap) {

    Set<Long> userIds =
            getProjectUserIds(
                    projectId,
                    userType,
                    relationMap);

    List<UserBasicVO> result =
            new ArrayList<>(userIds.size());

    for (Long userId : userIds) {
      UserBasicVO user =
              userMap.get(userId);

      if (user != null) {
        result.add(user);
      }
    }

    return result;
  }

  /**
   * 构建用户简要信息映射。
   */
  private Map<Long, UserBriefVO> buildUserBriefMap(
          List<UserBriefVO> userList) {

    Map<Long, UserBriefVO> result =
            new HashMap<>();

    if (CollectionUtils.isEmpty(userList)) {
      return result;
    }

    for (UserBriefVO user : userList) {
      if (user != null
              && user.getId() != null) {

        result.put(user.getId(), user);
      }
    }

    return result;
  }

  /**
   * 构建用户基础信息映射。
   */
  private Map<Long, UserBasicVO> buildUserBasicMap(
          List<UserBasicVO> userList) {

    Map<Long, UserBasicVO> result =
            new HashMap<>();

    if (CollectionUtils.isEmpty(userList)) {
      return result;
    }

    for (UserBasicVO user : userList) {
      if (user != null
              && user.getId() != null) {

        result.put(user.getId(), user);
      }
    }

    return result;
  }

  /**
   * 获取必须存在的项目。
   */
  private Project getRequiredProject(
          Long projectId) {

    if (projectId == null) {
      throw new IllegalArgumentException(
              "项目 ID 不能为空");
    }

    Project project =
            projectDao.selectByProjectId(
                    projectId);

    if (project == null) {
      throw new YakSecurityException(
              ResultCode.PROJECT_NOT_EXISTS);
    }

    return project;
  }

  /**
   * 校验项目保存参数。
   */
  private void checkParam(
          ProjectSaveDTO projectSaveDTO,
          boolean update) {

    if (projectSaveDTO == null) {
      throw new IllegalArgumentException(
              "项目信息不能为空");
    }

    if (!StringUtils.hasText(
            projectSaveDTO.getProjectName())) {

      throw new YakSecurityException(
              ResultCode
                      .PROJECT_NAME_CANNOT_BE_BLANK);
    }

    projectSaveDTO.setProjectName(
            projectSaveDTO
                    .getProjectName()
                    .trim());

    Long excludeProjectId = null;

    if (update) {
      if (projectSaveDTO.getId() == null) {
        throw new IllegalArgumentException(
                "项目 ID 不能为空");
      }

      excludeProjectId =
              projectSaveDTO.getId();
    }

    int duplicateCount =
            projectDao
                    .selectCountByProjectNameAndNotProjectId(
                            projectSaveDTO
                                    .getProjectName(),
                            excludeProjectId);

    if (duplicateCount > 0) {
      throw new YakSecurityException(
              ResultCode
                      .PROJECT_NAME_ALREADY_EXISTS);
    }
  }

  /**
   * 查询项目资源名称。
   */
  private List<String> listResourceOfProject(
          Long projectId) {

    if (projectId == null) {
      return new ArrayList<>();
    }

    Project project =
            projectDao.selectByProjectId(
                    projectId);

    if (project == null) {
      return new ArrayList<>();
    }

    List<ResourceDTO> resourceList =
            resourceExtend.getResourceList(
                    projectId,
                    null);

    if (CollectionUtils.isEmpty(resourceList)) {
      return new ArrayList<>();
    }

    return resourceList.stream()
            .filter(Objects::nonNull)
            .map(ResourceDTO::getResourceName)
            .filter(StringUtils::hasText)
            .collect(Collectors.toList());
  }

  /**
   * 保存项目关系操作日志。
   */
  private void saveRelationOplog(
          String operator,
          String operation,
          Project project,
          String content) {

    oplogService.saveOplog(
            new OplogDTO(
                    operator,
                    operation,
                    OPERATION_OBJECT_PROJECT,
                    project.getProjectName(),
                    content));
  }

  /**
   * 校验用户 ID。
   */
  private void checkUserId(Long userId) {
    if (userId == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }
  }

  /**
   * 将持久化分页元数据转换为统一 HTTP 分页数据。
   */
  private static <T> PagingData<T> toPagingData(
          List<T> records,
          IPage<?> page) {
    return PagingData.from(
            new PageData<>(
                    records,
                    page.getTotal(),
                    page.getPages(),
                    page.getCurrent(),
                    page.getSize()));
  }

  /**
   * 生成项目编码。
   */
  private String generateProjectCode() {
    return PROJECT_CODE_PREFIX
            + MathUtil.getRandomNumber(
            PROJECT_CODE_RANDOM_LENGTH);
  }

  /**
   * 过滤空 ID 并去重。
   */
  private List<Long> normalizeIds(
          List<Long> idList) {

    if (CollectionUtils.isEmpty(idList)) {
      return new ArrayList<>();
    }

    return idList.stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
  }
}