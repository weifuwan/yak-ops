package io.yak.ops.api.audit.operator;

import io.yak.ops.api.audit.OperatorLogAspect;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.dao.entity.AuditLog;

import java.util.List;
import java.util.Map;

public interface AuditOperator {

    void recordAudit(OperatorLogAspect.AuditContext auditContext, Object returnValue);

    void setRequestParam(AuditType auditType, List<AuditLog> auditLogList, Map<String, Object> paramsMap);
}
