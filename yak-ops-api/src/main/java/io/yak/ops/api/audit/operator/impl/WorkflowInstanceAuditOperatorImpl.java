package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.OperatorUtils;
import io.yak.ops.api.audit.constants.AuditLogConstants;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.common.enums.AuditOperationType;
import io.yak.ops.dao.entity.AuditLog;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.dao.repository.WorkflowInstanceDao;

import org.apache.commons.lang3.math.NumberUtils;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkflowInstanceAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private WorkflowInstanceDao workflowInstanceDao;

    @Override
    public void modifyAuditOperationType(AuditType auditType, Map<String, Object> paramsMap,
                                         List<AuditLog> auditLogList) {
        AuditOperationType auditOperationType = OperatorUtils.modifyReleaseOperationType(auditType, paramsMap);
        auditLogList.forEach(auditLog -> auditLog.setOperationType(auditOperationType.getName()));
    }

    @Override
    protected void setObjectByParam(String[] paramNameArr, Map<String, Object> paramsMap,
                                    List<AuditLog> auditLogList) {
        if (paramNameArr[0].equals(AuditLogConstants.WORKFLOW_INSTANCE_IDS)) {
            super.setObjectByParamArr(paramNameArr, paramsMap, auditLogList);
        } else {
            super.setObjectByParam(paramNameArr, paramsMap, auditLogList);
        }
    }

    @Override
    protected String getObjectNameFromIdentity(Object identity) {
        int objId = NumberUtils.toInt(identity.toString(), -1);
        if (objId == -1) {
            return "";
        }

        WorkflowInstance obj = workflowInstanceDao.queryDetailById(objId);
        return obj == null ? "" : obj.getName();
    }
}
