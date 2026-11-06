package io.yak.ops.api.audit.enums;

import static io.yak.ops.api.audit.constants.AuditLogConstants.ALIAS;
import static io.yak.ops.api.audit.constants.AuditLogConstants.CLUSTER_CODE;
import static io.yak.ops.api.audit.constants.AuditLogConstants.CODE;
import static io.yak.ops.api.audit.constants.AuditLogConstants.CODES;
import static io.yak.ops.api.audit.constants.AuditLogConstants.ENVIRONMENT_CODE;
import static io.yak.ops.api.audit.constants.AuditLogConstants.FILE_NAME;
import static io.yak.ops.api.audit.constants.AuditLogConstants.FULL_NAME;
import static io.yak.ops.api.audit.constants.AuditLogConstants.ID;
import static io.yak.ops.api.audit.constants.AuditLogConstants.NAME;
import static io.yak.ops.api.audit.constants.AuditLogConstants.PRIORITY;
import static io.yak.ops.api.audit.constants.AuditLogConstants.QUEUE_ID;
import static io.yak.ops.api.audit.constants.AuditLogConstants.TASK_CODE;
import static io.yak.ops.api.audit.constants.AuditLogConstants.TYPE;
import static io.yak.ops.api.audit.constants.AuditLogConstants.USER_ID;
import static io.yak.ops.api.audit.constants.AuditLogConstants.VERSION;
import static io.yak.ops.api.audit.constants.AuditLogConstants.WORKFLOW_DEFINITION_CODE;
import static io.yak.ops.api.audit.constants.AuditLogConstants.WORKFLOW_DEFINITION_CODES;
import static io.yak.ops.api.audit.constants.AuditLogConstants.WORKFLOW_INSTANCE_ID;
import static io.yak.ops.api.audit.constants.AuditLogConstants.WORKFLOW_INSTANCE_IDS;
import static io.yak.ops.common.enums.AuditModelType.ALARM_GROUP;
import static io.yak.ops.common.enums.AuditModelType.ALARM_INSTANCE;
import static io.yak.ops.common.enums.AuditModelType.CLUSTER;
import static io.yak.ops.common.enums.AuditModelType.DATASOURCE;
import static io.yak.ops.common.enums.AuditModelType.ENVIRONMENT;
import static io.yak.ops.common.enums.AuditModelType.FILE;
import static io.yak.ops.common.enums.AuditModelType.FOLDER;
import static io.yak.ops.common.enums.AuditModelType.PROJECT;
import static io.yak.ops.common.enums.AuditModelType.SCHEDULE;
import static io.yak.ops.common.enums.AuditModelType.TASK;
import static io.yak.ops.common.enums.AuditModelType.TASK_GROUP;
import static io.yak.ops.common.enums.AuditModelType.TASK_INSTANCE;
import static io.yak.ops.common.enums.AuditModelType.TENANT;
import static io.yak.ops.common.enums.AuditModelType.TOKEN;
import static io.yak.ops.common.enums.AuditModelType.USER;
import static io.yak.ops.common.enums.AuditModelType.WORKER_GROUP;
import static io.yak.ops.common.enums.AuditModelType.WORKFLOW;
import static io.yak.ops.common.enums.AuditModelType.WORKFLOW_INSTANCE;
import static io.yak.ops.common.enums.AuditModelType.YARN_QUEUE;
import static io.yak.ops.common.enums.AuditOperationType.BATCH_DELETE;
import static io.yak.ops.common.enums.AuditOperationType.BATCH_RERUN;
import static io.yak.ops.common.enums.AuditOperationType.BATCH_START;
import static io.yak.ops.common.enums.AuditOperationType.CLOSE;
import static io.yak.ops.common.enums.AuditOperationType.COPY;
import static io.yak.ops.common.enums.AuditOperationType.CREATE;
import static io.yak.ops.common.enums.AuditOperationType.DELETE;
import static io.yak.ops.common.enums.AuditOperationType.DELETE_VERSION;
import static io.yak.ops.common.enums.AuditOperationType.EXECUTE;
import static io.yak.ops.common.enums.AuditOperationType.EXPORT;
import static io.yak.ops.common.enums.AuditOperationType.FORCE_SUCCESS;
import static io.yak.ops.common.enums.AuditOperationType.IMPORT;
import static io.yak.ops.common.enums.AuditOperationType.MODIFY;
import static io.yak.ops.common.enums.AuditOperationType.OFFLINE;
import static io.yak.ops.common.enums.AuditOperationType.ONLINE;
import static io.yak.ops.common.enums.AuditOperationType.RELEASE;
import static io.yak.ops.common.enums.AuditOperationType.START;
import static io.yak.ops.common.enums.AuditOperationType.STOP;
import static io.yak.ops.common.enums.AuditOperationType.SWITCH_VERSION;
import static io.yak.ops.common.enums.AuditOperationType.UPDATE;

import io.yak.ops.api.audit.constants.AuditLogConstants;
import io.yak.ops.api.audit.operator.AuditOperator;
import io.yak.ops.api.audit.operator.impl.AlertGroupAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.AlertInstanceAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.ClusterAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.DatasourceAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.EnvironmentAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.ProjectAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.ResourceAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.ScheduleAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.TaskAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.TaskGroupAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.TaskInstancesAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.TenantAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.TokenAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.UserAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.WorkerGroupAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.WorkflowAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.WorkflowInstanceAuditOperatorImpl;
import io.yak.ops.api.audit.operator.impl.YarnQueueAuditOperatorImpl;
import io.yak.ops.common.enums.AuditModelType;
import io.yak.ops.common.enums.AuditOperationType;

import lombok.Getter;

@Getter
public enum AuditType {

    PROJECT_CREATE(PROJECT, CREATE, ProjectAuditOperatorImpl.class, new String[]{}, new String[]{CODE}),
    PROJECT_UPDATE(PROJECT, UPDATE, ProjectAuditOperatorImpl.class, new String[]{}, new String[]{CODE}),
    PROJECT_DELETE(PROJECT, DELETE, ProjectAuditOperatorImpl.class, new String[]{CODE}, new String[]{}),

    WORKFLOW_CREATE(WORKFLOW, CREATE, WorkflowAuditOperatorImpl.class, new String[]{}, new String[]{CODE}),
    WORKFLOW_UPDATE(WORKFLOW, UPDATE, WorkflowAuditOperatorImpl.class, new String[]{}, new String[]{CODE}),
    WORKFLOW_SWITCH_VERSION(WORKFLOW, SWITCH_VERSION, WorkflowAuditOperatorImpl.class, new String[]{CODE, VERSION},
            new String[]{}),
    WORKFLOW_DELETE_VERSION(WORKFLOW, DELETE_VERSION, WorkflowAuditOperatorImpl.class, new String[]{CODE, VERSION},
            new String[]{}),
    WORKFLOW_RELEASE(WORKFLOW, RELEASE, WorkflowAuditOperatorImpl.class, new String[]{WORKFLOW_DEFINITION_CODE},
            new String[]{}),
    WORKFLOW_COPY(WORKFLOW, COPY, WorkflowAuditOperatorImpl.class, new String[]{CODES}, new String[]{}),
    WORKFLOW_EXPORT(WORKFLOW, EXPORT, WorkflowAuditOperatorImpl.class, new String[]{CODES}, new String[]{}),
    WORKFLOW_DELETE(WORKFLOW, DELETE, WorkflowAuditOperatorImpl.class, new String[]{CODE}, new String[]{}),
    WORKFLOW_BATCH_DELETE(WORKFLOW, BATCH_DELETE, WorkflowAuditOperatorImpl.class, new String[]{CODES}, new String[]{}),
    WORKFLOW_START(WORKFLOW, START, WorkflowAuditOperatorImpl.class,
            new String[]{AuditLogConstants.WORKFLOW_DEFINITION_CODE},
            new String[]{}),
    WORKFLOW_BATCH_START(WORKFLOW, BATCH_START, WorkflowAuditOperatorImpl.class,
            new String[]{WORKFLOW_DEFINITION_CODES},
            new String[]{}),
    WORKFLOW_BATCH_RERUN(WORKFLOW, BATCH_RERUN, WorkflowInstanceAuditOperatorImpl.class,
            new String[]{WORKFLOW_INSTANCE_IDS},
            new String[]{}),
    WORKFLOW_EXECUTE(WORKFLOW, EXECUTE, WorkflowInstanceAuditOperatorImpl.class, new String[]{WORKFLOW_INSTANCE_ID},
            new String[]{}),
    WORKFLOW_IMPORT(WORKFLOW, IMPORT, WorkflowAuditOperatorImpl.class, new String[]{}, new String[]{CODE}),
    WORKFLOW_INSTANCE_UPDATE(WORKFLOW_INSTANCE, UPDATE, WorkflowInstanceAuditOperatorImpl.class, new String[]{ID},
            new String[]{}),
    WORKFLOW_INSTANCE_DELETE(WORKFLOW_INSTANCE, DELETE, WorkflowInstanceAuditOperatorImpl.class, new String[]{ID},
            new String[]{}),
    WORKFLOW_INSTANCE_BATCH_DELETE(WORKFLOW_INSTANCE, BATCH_DELETE, WorkflowInstanceAuditOperatorImpl.class,
            new String[]{WORKFLOW_INSTANCE_IDS}, new String[]{}),

    TASK_CREATE(TASK, CREATE, TaskAuditOperatorImpl.class, new String[]{}, new String[]{CODE}),
    TASK_UPDATE(TASK, UPDATE, TaskAuditOperatorImpl.class, new String[]{}, new String[]{CODE}),
    TASK_SWITCH_VERSION(TASK, SWITCH_VERSION, TaskAuditOperatorImpl.class, new String[]{CODE, VERSION}, new String[]{}),
    TASK_DELETE_VERSION(TASK, DELETE_VERSION, TaskAuditOperatorImpl.class, new String[]{CODE, VERSION}, new String[]{}),
    TASK_DELETE(TASK, DELETE, TaskAuditOperatorImpl.class, new String[]{CODE}, new String[]{}),
    TASK_RELEASE(TASK, RELEASE, TaskAuditOperatorImpl.class, new String[]{CODE}, new String[]{}),
    TASK_START(TASK, START, TaskAuditOperatorImpl.class, new String[]{CODE}, new String[]{}),
    TASK_INSTANCE_FORCE_SUCCESS(TASK_INSTANCE, FORCE_SUCCESS, TaskInstancesAuditOperatorImpl.class, new String[]{ID},
            new String[]{}),

    DATA_SYNC_CREATE(TASK, CREATE, TaskAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    DATA_SYNC_UPDATE(TASK, UPDATE, TaskAuditOperatorImpl.class, new String[]{TASK_CODE}, new String[]{}),
    DATA_SYNC_DELETE(TASK, DELETE, TaskAuditOperatorImpl.class, new String[]{TASK_CODE}, new String[]{}),
    DATA_SYNC_PUBLISH(TASK, ONLINE, TaskAuditOperatorImpl.class, new String[]{TASK_CODE}, new String[]{}),
    DATA_SYNC_UNPUBLISH(TASK, OFFLINE, TaskAuditOperatorImpl.class, new String[]{TASK_CODE}, new String[]{}),
    DATA_SYNC_START(TASK, START, TaskAuditOperatorImpl.class, new String[]{TASK_CODE}, new String[]{}),
    DATA_SYNC_STOP(TASK_INSTANCE, STOP, TaskInstancesAuditOperatorImpl.class, new String[]{ID}, new String[]{}),

    SCHEDULE_CREATE(SCHEDULE, CREATE, ScheduleAuditOperatorImpl.class,
            new String[]{AuditLogConstants.WORKFLOW_DEFINITION_CODE},
            new String[]{ID}),
    SCHEDULE_UPDATE(SCHEDULE, UPDATE, ScheduleAuditOperatorImpl.class, new String[]{ID}, new String[]{}),
    SCHEDULE_ONLINE(SCHEDULE, ONLINE, ScheduleAuditOperatorImpl.class, new String[]{ID}, new String[]{}),
    SCHEDULE_OFFLINE(SCHEDULE, OFFLINE, ScheduleAuditOperatorImpl.class, new String[]{ID}, new String[]{}),
    SCHEDULE_DELETE(SCHEDULE, DELETE, ScheduleAuditOperatorImpl.class, new String[]{ID}, new String[]{}),

    FOLDER_CREATE(FOLDER, CREATE, ResourceAuditOperatorImpl.class, new String[]{TYPE, ALIAS}, new String[]{}),
    FILE_CREATE(FILE, CREATE, ResourceAuditOperatorImpl.class, new String[]{TYPE, FILE_NAME, ALIAS}, new String[]{}),
    FILE_UPDATE(FILE, UPDATE, ResourceAuditOperatorImpl.class, new String[]{TYPE, FILE_NAME, ALIAS}, new String[]{}),
    FILE_DELETE(FILE, DELETE, ResourceAuditOperatorImpl.class, new String[]{FULL_NAME}, new String[]{}),

    TASK_GROUP_CREATE(TASK_GROUP, CREATE, TaskGroupAuditOperatorImpl.class, new String[]{NAME}, new String[]{}),
    TASK_GROUP_UPDATE(TASK_GROUP, UPDATE, TaskGroupAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    TASK_GROUP_CLOSE(TASK_GROUP, CLOSE, TaskGroupAuditOperatorImpl.class, new String[]{ID}, new String[]{}),
    TASK_GROUP_START(TASK_GROUP, START, TaskGroupAuditOperatorImpl.class, new String[]{ID}, new String[]{}),
    TASK_GROUP_MODIFY(TASK_GROUP, MODIFY, TaskGroupAuditOperatorImpl.class, new String[]{QUEUE_ID, PRIORITY},
            new String[]{}),

    DATASOURCE_CREATE(DATASOURCE, CREATE, DatasourceAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    DATASOURCE_UPDATE(DATASOURCE, UPDATE, DatasourceAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    DATASOURCE_DELETE(DATASOURCE, DELETE, DatasourceAuditOperatorImpl.class, new String[]{ID}, new String[]{}),

    TENANT_CREATE(TENANT, CREATE, TenantAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    TENANT_UPDATE(TENANT, UPDATE, TenantAuditOperatorImpl.class, new String[]{ID}, new String[]{}),
    TENANT_DELETE(TENANT, DELETE, TenantAuditOperatorImpl.class, new String[]{ID}, new String[]{}),

    USER_CREATE(USER, CREATE, UserAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    USER_UPDATE(USER, UPDATE, UserAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    USER_DELETE(USER, DELETE, UserAuditOperatorImpl.class, new String[]{ID}, new String[]{}),

    ALARM_GROUP_CREATE(ALARM_GROUP, CREATE, AlertGroupAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    ALARM_GROUP_UPDATE(ALARM_GROUP, UPDATE, AlertGroupAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    ALARM_GROUP_DELETE(ALARM_GROUP, DELETE, AlertGroupAuditOperatorImpl.class, new String[]{ID}, new String[]{}),

    ALARM_INSTANCE_CREATE(ALARM_INSTANCE, CREATE, AlertInstanceAuditOperatorImpl.class, new String[]{},
            new String[]{ID}),
    ALARM_INSTANCE_UPDATE(ALARM_INSTANCE, UPDATE, AlertInstanceAuditOperatorImpl.class, new String[]{},
            new String[]{ID}),
    ALARM_INSTANCE_DELETE(ALARM_INSTANCE, DELETE, AlertInstanceAuditOperatorImpl.class, new String[]{ID},
            new String[]{}),

    WORKER_GROUP_CREATE(WORKER_GROUP, CREATE, WorkerGroupAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    WORKER_GROUP_DELETE(WORKER_GROUP, DELETE, WorkerGroupAuditOperatorImpl.class, new String[]{ID}, new String[]{}),

    YARN_QUEUE_CREATE(YARN_QUEUE, CREATE, YarnQueueAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    YARN_QUEUE_UPDATE(YARN_QUEUE, UPDATE, YarnQueueAuditOperatorImpl.class, new String[]{}, new String[]{ID}),
    YARN_QUEUE_DELETE(YARN_QUEUE, DELETE, YarnQueueAuditOperatorImpl.class, new String[]{ID}, new String[]{}),

    ENVIRONMENT_CREATE(ENVIRONMENT, CREATE, EnvironmentAuditOperatorImpl.class, new String[]{}, new String[]{CODE}),
    ENVIRONMENT_UPDATE(ENVIRONMENT, UPDATE, EnvironmentAuditOperatorImpl.class, new String[]{}, new String[]{CODE}),
    ENVIRONMENT_DELETE(ENVIRONMENT, DELETE, EnvironmentAuditOperatorImpl.class, new String[]{ENVIRONMENT_CODE},
            new String[]{}),

    CLUSTER_CREATE(CLUSTER, CREATE, ClusterAuditOperatorImpl.class, new String[]{}, new String[]{CODE}),
    CLUSTER_UPDATE(CLUSTER, UPDATE, ClusterAuditOperatorImpl.class, new String[]{}, new String[]{CODE}),
    CLUSTER_DELETE(CLUSTER, DELETE, ClusterAuditOperatorImpl.class, new String[]{CLUSTER_CODE}, new String[]{}),

    TOKEN_CREATE(TOKEN, CREATE, TokenAuditOperatorImpl.class, new String[]{}, new String[]{USER_ID}),
    TOKEN_UPDATE(TOKEN, UPDATE, TokenAuditOperatorImpl.class, new String[]{}, new String[]{USER_ID}),
    TOKEN_DELETE(TOKEN, DELETE, TokenAuditOperatorImpl.class, new String[]{ID}, new String[]{}),
    ;

    private final Class<? extends AuditOperator> operatorClass;
    private final AuditModelType auditModelType;
    private final AuditOperationType auditOperationType;

    /**
     * The names of the fields in the API request to be recorded.
     * Represents an array of key-value pairs, e.g., [ID, "status"].
     */
    private final String[] requestParamName;

    /**
     * The names of the fields in the returned object to be recorded.
     * Represents an array of field names, e.g., [ID, CODE].
     * Specify the field names to record from the returned object.
     */
    private final String[] returnObjectFieldName;

    AuditType(AuditModelType auditModelType, AuditOperationType auditOperationType,
              Class<? extends AuditOperator> operatorClass, String[] requestParamName, String[] returnObjectFieldName) {
        this.auditModelType = auditModelType;
        this.auditOperationType = auditOperationType;
        this.operatorClass = operatorClass;
        this.requestParamName = requestParamName;
        this.returnObjectFieldName = returnObjectFieldName;
    }
}
