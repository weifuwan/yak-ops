package io.yak.ops.common.enums;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import lombok.Getter;

@Getter
public enum AuditModelType {

    PROJECT("Project", null),
    WORKFLOW("Workflow", PROJECT),
    WORKFLOW_INSTANCE("WorkflowInstance", WORKFLOW),
    TASK("Task", WORKFLOW),
    TASK_INSTANCE("TaskInstance", TASK),
    SCHEDULE("Schedule", WORKFLOW),

    RESOURCE("Resource", null),
    FOLDER("Folder", RESOURCE),
    FILE("File", FOLDER),
    TASK_GROUP("TaskGroup", RESOURCE),

    DATASOURCE("Datasource", null),

    SECURITY("Security", null),
    TENANT("Tenant", SECURITY),
    USER("User", SECURITY),
    ALARM_GROUP("AlarmGroup", SECURITY),
    ALARM_INSTANCE("AlarmInstance", SECURITY),
    WORKER_GROUP("WorkerGroup", SECURITY),
    YARN_QUEUE("YarnQueue", SECURITY),
    ENVIRONMENT("Environment", SECURITY),
    CLUSTER("Cluster", SECURITY),
    TOKEN("Token", SECURITY),
    ;
    private final String name;
    private final AuditModelType parentType;
    private final List<AuditModelType> child = new ArrayList<>();

    private static final HashMap<String, AuditModelType> AUDIT_MODEL_MAP = new HashMap<>();
    private static final List<AuditModelType> AUDIT_MODEL_TREE_LIST = new ArrayList<>();

    static {
        for (AuditModelType auditModelType : values()) {
            AUDIT_MODEL_MAP.put(auditModelType.name, auditModelType);
        }

        for (AuditModelType auditModelType : values()) {
            if (auditModelType.parentType != null) {
                of(auditModelType.parentType.name).child.add(auditModelType);
            } else {
                AUDIT_MODEL_TREE_LIST.add(auditModelType);
            }
        }
    }

    public static List<AuditModelType> getAuditModelTreeList() {
        return AUDIT_MODEL_TREE_LIST;
    }

    public static AuditModelType of(String name) {
        if (AUDIT_MODEL_MAP.containsKey(name)) {
            return AUDIT_MODEL_MAP.get(name);
        }

        throw new IllegalArgumentException("invalid audit operation type name " + name);
    }

    AuditModelType(String name, AuditModelType parentType) {
        this.name = name;
        this.parentType = parentType;
    }
}
