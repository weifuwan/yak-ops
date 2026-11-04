package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.OperatorUtils;
import io.yak.ops.api.audit.constants.AuditLogConstants;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.common.enums.AuditOperationType;
import io.yak.ops.dao.entity.AuditLog;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.repository.TaskDefinitionDao;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private TaskDefinitionDao taskDefinitionDao;

    @Override
    public void modifyAuditOperationType(AuditType auditType, Map<String, Object> paramsMap,
                                         List<AuditLog> auditLogList) {
        AuditOperationType auditOperationType = OperatorUtils.modifyReleaseOperationType(auditType, paramsMap);
        auditLogList.forEach(auditLog -> auditLog.setOperationType(auditOperationType.getName()));
    }

    @Override
    protected void setObjectByParam(String[] paramNameArr, Map<String, Object> paramsMap,
                                    List<AuditLog> auditLogList) {

        super.setObjectByParam(paramNameArr, paramsMap, auditLogList);
        if (paramsMap.containsKey(AuditLogConstants.VERSION)) {
            auditLogList.get(0).setDetail(paramsMap.get(AuditLogConstants.VERSION).toString());
        }
    }

    @Override
    protected String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        TaskDefinition obj = taskDefinitionDao.queryByCode(objId);
        return obj == null ? "" : obj.getName();
    }
}
