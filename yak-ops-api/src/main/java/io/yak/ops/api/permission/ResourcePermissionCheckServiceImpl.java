package io.yak.ops.api.permission;

import static java.util.stream.Collectors.toSet;

import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.AccessToken;
import io.yak.ops.dao.entity.AlertGroup;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.entity.Environment;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.Queue;
import io.yak.ops.dao.entity.TaskGroup;
import io.yak.ops.dao.entity.Tenant;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkerGroup;
import io.yak.ops.dao.mapper.AlertPluginInstanceMapper;
import io.yak.ops.dao.mapper.EnvironmentMapper;
import io.yak.ops.dao.mapper.TaskGroupMapper;
import io.yak.ops.dao.repository.AccessTokenDao;
import io.yak.ops.dao.repository.AlertGroupDao;
import io.yak.ops.dao.repository.DataSourceDao;
import io.yak.ops.dao.repository.ProjectDao;
import io.yak.ops.dao.repository.QueueDao;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.dao.repository.UserDao;
import io.yak.ops.dao.repository.WorkerGroupDao;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ResourcePermissionCheckServiceImpl
        implements
            ResourcePermissionCheckService<Object>,
            ApplicationContextAware {

    @Autowired
    private UserDao userDao;

    static final Map<AuthorizationType, ResourceAcquisitionAndPermissionCheck<?>> RESOURCE_LIST_MAP =
            new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        for (ResourceAcquisitionAndPermissionCheck<?> authorizedResourceList : applicationContext
                .getBeansOfType(ResourceAcquisitionAndPermissionCheck.class).values()) {
            List<AuthorizationType> authorizationTypes = authorizedResourceList.authorizationTypes();
            authorizationTypes.forEach(auth -> RESOURCE_LIST_MAP.put(auth, authorizedResourceList));
        }
    }

    @Override
    public boolean resourcePermissionCheck(Object authorizationType, Object[] needChecks, Integer userId,
                                           Logger logger) {
        if (Objects.nonNull(needChecks) && needChecks.length > 0) {
            Set<?> originResSet = new HashSet<>(Arrays.asList(needChecks));
            Set<?> ownResSets = RESOURCE_LIST_MAP.get(authorizationType).listAuthorizedResourceIds(userId, logger);
            boolean checkResult = ownResSets != null && ownResSets.containsAll(originResSet);
            if (!checkResult) {
                logger.warn("User does not have resource permission on associated resources, userId:{}", userId);
            }
            return checkResult;
        }
        return true;
    }

    @Override
    public boolean operationPermissionCheck(AuthorizationType authorizationType, Integer userId, String permissionKey,
                                            Logger logger) {
        User user = userDao.queryById(userId);
        if (user == null) {
            logger.error("User does not exist, userId:{}.", userId);
            return false;
        }
        if (user.getUserType().equals(UserType.ADMIN_USER)) {
            return true;
        }
        return RESOURCE_LIST_MAP.get(authorizationType).permissionCheck(userId, permissionKey, logger);
    }

    @Override
    public Set<Object> userOwnedResourceIdsAcquisition(Object authorizationType, Integer userId, Logger logger) {
        User user = userDao.queryById(userId);
        if (user == null) {
            logger.error("User does not exist, userId:{}.", userId);
            return Collections.emptySet();
        }
        return (Set<Object>) RESOURCE_LIST_MAP.get(authorizationType).listAuthorizedResourceIds(
                user.getUserType().equals(UserType.ADMIN_USER) ? 0 : userId, logger);
    }

    @Component
    public static class QueueResourcePermissionCheck implements ResourceAcquisitionAndPermissionCheck<Integer> {

        private final QueueDao queueDao;

        public QueueResourcePermissionCheck(QueueDao queueDao) {
            this.queueDao = queueDao;
        }

        @Override
        public List<AuthorizationType> authorizationTypes() {
            return Collections.singletonList(AuthorizationType.QUEUE);
        }

        @Override
        public boolean permissionCheck(int userId, String permissionKey, Logger logger) {
            return false;
        }

        @Override
        public Set<Integer> listAuthorizedResourceIds(int userId, Logger logger) {
            if (userId != 0) {
                return Collections.emptySet();
            }
            List<Queue> queues = queueDao.queryAll();
            return queues.stream().map(Queue::getId).collect(toSet());
        }
    }

    @Component
    public static class ProjectsResourcePermissionCheck implements ResourceAcquisitionAndPermissionCheck<Integer> {

        private final ProjectDao projectDao;

        public ProjectsResourcePermissionCheck(ProjectDao projectDao) {
            this.projectDao = projectDao;
        }

        @Override
        public List<AuthorizationType> authorizationTypes() {
            return Collections.singletonList(AuthorizationType.PROJECTS);
        }

        @Override
        public boolean permissionCheck(int userId, String permissionKey, Logger logger) {
            // all users can create projects
            return true;
        }

        @Override
        public Set<Integer> listAuthorizedResourceIds(int userId, Logger logger) {
            return projectDao.listAuthorizedProjects(userId, null).stream().map(Project::getId).collect(toSet());
        }
    }

    @Component
    public static class TaskGroupPermissionCheck implements ResourceAcquisitionAndPermissionCheck<Integer> {

        private final TaskGroupMapper taskGroupMapper;

        public TaskGroupPermissionCheck(TaskGroupMapper taskGroupMapper) {
            this.taskGroupMapper = taskGroupMapper;
        }

        @Override
        public List<AuthorizationType> authorizationTypes() {
            return Collections.singletonList(AuthorizationType.TASK_GROUP);
        }

        @Override
        public Set<Integer> listAuthorizedResourceIds(int userId, Logger logger) {
            List<TaskGroup> taskGroupList = taskGroupMapper.listAuthorizedResource(userId);
            return taskGroupList.stream().map(TaskGroup::getId).collect(Collectors.toSet());
        }

        @Override
        public boolean permissionCheck(int userId, String permissionKey, Logger logger) {
            return true;
        }
    }

    @Component
    public static class EnvironmentResourcePermissionCheck implements ResourceAcquisitionAndPermissionCheck<Integer> {

        private final EnvironmentMapper environmentMapper;

        private final UserDao userDao;

        public EnvironmentResourcePermissionCheck(EnvironmentMapper environmentMapper, UserDao userDao) {
            this.environmentMapper = environmentMapper;
            this.userDao = userDao;
        }

        @Override
        public List<AuthorizationType> authorizationTypes() {
            return Collections.singletonList(AuthorizationType.ENVIRONMENT);
        }

        @Override
        public boolean permissionCheck(int userId, String url, Logger logger) {
            User user = userDao.queryById(userId);
            if (user == null) {
                logger.error("User does not exist, userId:{}.", userId);
                return false;
            }
            return user.getUserType() == UserType.ADMIN_USER;
        }

        @Override
        public Set<Integer> listAuthorizedResourceIds(int userId, Logger logger) {
            List<Environment> environments = environmentMapper.queryAllEnvironmentList();
            return environments.stream().map(Environment::getId).collect(Collectors.toSet());
        }
    }

    @Component
    public static class WorkerGroupResourcePermissionCheck implements ResourceAcquisitionAndPermissionCheck<Integer> {

        private final WorkerGroupDao workerGroupDao;

        public WorkerGroupResourcePermissionCheck(WorkerGroupDao workerGroupDao) {
            this.workerGroupDao = workerGroupDao;
        }

        @Override
        public List<AuthorizationType> authorizationTypes() {
            return Collections.singletonList(AuthorizationType.WORKER_GROUP);
        }

        @Override
        public boolean permissionCheck(int userId, String url, Logger logger) {
            return false;
        }

        @Override
        public Set<Integer> listAuthorizedResourceIds(int userId, Logger logger) {
            List<WorkerGroup> workerGroups = workerGroupDao.queryAllWorkerGroup();
            return workerGroups.stream().map(WorkerGroup::getId).collect(Collectors.toSet());
        }
    }

    /**
     * AlertPluginInstance Resource
     */
    @Component
    public static class AlertPluginInstanceResourcePermissionCheck
            implements
                ResourceAcquisitionAndPermissionCheck<Integer> {

        private final AlertPluginInstanceMapper alertPluginInstanceMapper;

        public AlertPluginInstanceResourcePermissionCheck(AlertPluginInstanceMapper alertPluginInstanceMapper) {
            this.alertPluginInstanceMapper = alertPluginInstanceMapper;
        }

        @Override
        public List<AuthorizationType> authorizationTypes() {
            return Collections.singletonList(AuthorizationType.ALERT_PLUGIN_INSTANCE);
        }

        @Override
        public boolean permissionCheck(int userId, String url, Logger logger) {
            return false;
        }

        @Override
        public Set<Integer> listAuthorizedResourceIds(int userId, Logger logger) {
            return Collections.emptySet();
        }
    }

    /**
     * AlertPluginInstance Resource
     */
    @Component
    public static class AlertGroupResourcePermissionCheck implements ResourceAcquisitionAndPermissionCheck<Integer> {

        private final AlertGroupDao alertGroupDao;

        public AlertGroupResourcePermissionCheck(AlertGroupDao alertGroupDao) {
            this.alertGroupDao = alertGroupDao;
        }

        @Override
        public List<AuthorizationType> authorizationTypes() {
            return Collections.singletonList(AuthorizationType.ALERT_GROUP);
        }

        @Override
        public boolean permissionCheck(int userId, String url, Logger logger) {
            return false;
        }

        @Override
        public Set<Integer> listAuthorizedResourceIds(int userId, Logger logger) {
            List<AlertGroup> alertGroupList = alertGroupDao.queryAllGroupList();
            return alertGroupList.stream().map(AlertGroup::getId).collect(toSet());
        }
    }

    /**
     * Tenant Resource
     */
    @Component
    public static class TenantResourcePermissionCheck implements ResourceAcquisitionAndPermissionCheck<Integer> {

        private final TenantDao tenantDao;

        public TenantResourcePermissionCheck(TenantDao tenantDao) {
            this.tenantDao = tenantDao;
        }

        @Override
        public List<AuthorizationType> authorizationTypes() {
            return Collections.singletonList(AuthorizationType.TENANT);
        }

        @Override
        public boolean permissionCheck(int userId, String url, Logger logger) {
            return false;
        }

        @Override
        public Set<Integer> listAuthorizedResourceIds(int userId, Logger logger) {
            List<Tenant> tenantList = tenantDao.queryAll();
            return tenantList.stream().map(Tenant::getId).collect(Collectors.toSet());
        }
    }

    /**
     * DataSource Resource
     */
    @Component
    public static class DataSourceResourcePermissionCheck implements ResourceAcquisitionAndPermissionCheck<Integer> {

        private final DataSourceDao dataSourceDao;

        public DataSourceResourcePermissionCheck(DataSourceDao dataSourceDao) {
            this.dataSourceDao = dataSourceDao;
        }

        @Override
        public List<AuthorizationType> authorizationTypes() {
            return Collections.singletonList(AuthorizationType.DATASOURCE);
        }

        @Override
        public boolean permissionCheck(int userId, String url, Logger logger) {
            return true;
        }

        @Override
        public Set<Integer> listAuthorizedResourceIds(int userId, Logger logger) {
            return dataSourceDao.listAuthorizedDataSource(userId, null).stream().map(DataSource::getId)
                    .collect(toSet());
        }
    }

    /**
     * AccessToken Resource
     */
    @Component
    public static class AccessTokenResourcePermissionCheck implements ResourceAcquisitionAndPermissionCheck<Integer> {

        private final AccessTokenDao accessTokenDao;

        public AccessTokenResourcePermissionCheck(AccessTokenDao accessTokenDao) {
            this.accessTokenDao = accessTokenDao;
        }

        @Override
        public List<AuthorizationType> authorizationTypes() {
            return Collections.singletonList(AuthorizationType.ACCESS_TOKEN);
        }

        @Override
        public boolean permissionCheck(int userId, String url, Logger logger) {
            return true;
        }

        @Override
        public Set<Integer> listAuthorizedResourceIds(int userId, Logger logger) {
            return accessTokenDao.listAuthorizedAccessToken(userId, null).stream().map(AccessToken::getId)
                    .collect(toSet());
        }
    }

    interface ResourceAcquisitionAndPermissionCheck<T> {

        /**
         * authorization types
         *
         * @return
         */
        List<AuthorizationType> authorizationTypes();

        /**
         * get all resources under the user (no admin)
         *
         * @param userId
         * @return
         */
        Set<T> listAuthorizedResourceIds(int userId, Logger logger);

        /**
         * permission check
         *
         * @param userId
         * @return
         */
        boolean permissionCheck(int userId, String permissionKey, Logger logger);

    }
}
