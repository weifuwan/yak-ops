package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.OperatorUtils;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.AuditLog;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class ResourceAuditOperatorImpl extends BaseAuditOperator {

    @Override
    public void modifyAuditObjectType(AuditType auditType, Map<String, Object> paramsMap, List<AuditLog> auditLogList) {
        auditLogList.forEach(auditLog -> auditLog
                .setModelType(OperatorUtils.getFileAuditObject(auditType, paramsMap, auditLog.getModelName())));
    }

    @Override
    protected void setObjectByParam(String[] paramNameArr, Map<String, Object> paramsMap,
                                    List<AuditLog> auditLogList) {

        Object objName = getFileNameFromParam(paramNameArr, paramsMap);

        if (objName == null) {
            return;
        }

        auditLogList.get(0).setModelName(objName.toString());
    }

    private String getFileNameFromParam(String[] paramNameArr, Map<String, Object> paramsMap) {
        for (String param : paramNameArr) {
            if (!param.equals("type") && paramsMap.containsKey(param)) {
                return paramsMap.get(param).toString();
            }
        }

        return null;
    }
}
