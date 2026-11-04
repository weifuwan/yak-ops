package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.OperatorUtils;
import io.yak.ops.api.audit.constants.AuditLogConstants;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.api.utils.Result;
import io.yak.ops.dao.entity.AuditLog;
import io.yak.ops.dao.entity.Schedule;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.repository.ScheduleDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ScheduleAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private ScheduleDao scheduleDao;

    @Autowired
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Override
    public void modifyRequestParams(String[] paramNameArr, Map<String, Object> paramsMap, List<AuditLog> auditLogList) {
        if (!paramNameArr[0].equals(AuditLogConstants.ID)) {
            return;
        }
        int id = (int) paramsMap.get(paramNameArr[0]);
        Schedule schedule = scheduleDao.queryById(id);
        if (schedule != null) {
            paramsMap.put(AuditLogConstants.CODE, schedule.getWorkflowDefinitionCode());
            paramNameArr[0] = AuditLogConstants.CODE;
            auditLogList.forEach(auditLog -> auditLog.setDetail(String.valueOf(id)));
        }
    }

    @Override
    protected void setObjectIdentityFromReturnObject(AuditType auditType, Result<?> result,
                                                     List<AuditLog> auditLogList) {
        String[] returnObjectFieldNameArr = auditType.getReturnObjectFieldName();
        if (returnObjectFieldNameArr.length == 0) {
            return;
        }

        Map<String, Object> returnObjectMap =
                OperatorUtils.getObjectIfFromReturnObject(result.getData(), returnObjectFieldNameArr);
        auditLogList
                .forEach(auditLog -> auditLog.setDetail(returnObjectMap.get(returnObjectFieldNameArr[0]).toString()));
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
