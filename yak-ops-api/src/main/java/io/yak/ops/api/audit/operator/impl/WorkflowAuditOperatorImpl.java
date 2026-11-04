package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.OperatorUtils;
import io.yak.ops.api.audit.constants.AuditLogConstants;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.common.enums.AuditOperationType;
import io.yak.ops.dao.entity.AuditLog;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkflowAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Override
    public void modifyAuditOperationType(AuditType auditType, Map<String, Object> paramsMap,
                                         List<AuditLog> auditLogList) {
        AuditOperationType auditOperationType = OperatorUtils.modifyReleaseOperationType(auditType, paramsMap);
        auditLogList.forEach(auditLog -> auditLog.setOperationType(auditOperationType.getName()));
    }

    @Override
    protected void setObjectByParam(String[] paramNameArr, Map<String, Object> paramsMap,
                                    List<AuditLog> auditLogList) {
        if (paramNameArr[0].equals(AuditLogConstants.CODES)
                || paramNameArr[0].equals(AuditLogConstants.WORKFLOW_DEFINITION_CODES)
                || paramNameArr[0].equals(AuditLogConstants.WORKFLOW_INSTANCE_IDS)) {
            super.setObjectByParamArr(paramNameArr, paramsMap, auditLogList);
        } else {
            super.setObjectByParam(paramNameArr, paramsMap, auditLogList);
        }
        if (paramsMap.containsKey(AuditLogConstants.VERSION)) {
            if (paramsMap.get(AuditLogConstants.VERSION) != null) {
                auditLogList.get(0).setDetail(paramsMap.get(AuditLogConstants.VERSION).toString());
            } else {
                auditLogList.get(0).setDetail("latest");
            }
        }
    }

    @Override
    protected String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        WorkflowDefinition obj = workflowDefinitionDao.queryByCode(objId).orElse(null);
        return obj == null ? "" : obj.getName();
    }
}
