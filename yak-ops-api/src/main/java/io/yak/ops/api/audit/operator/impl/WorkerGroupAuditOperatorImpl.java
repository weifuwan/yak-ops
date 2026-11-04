package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.constants.AuditLogConstants;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.common.enums.AuditOperationType;
import io.yak.ops.dao.entity.AuditLog;
import io.yak.ops.dao.entity.WorkerGroup;
import io.yak.ops.dao.repository.WorkerGroupDao;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkerGroupAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private WorkerGroupDao workerGroupDao;

    @Override
    public void modifyAuditOperationType(AuditType auditType, Map<String, Object> paramsMap,
                                         List<AuditLog> auditLogList) {
        if (auditType.getAuditOperationType() == AuditOperationType.CREATE
                && paramsMap.get(AuditLogConstants.ID) != null &&
                !paramsMap.get(AuditLogConstants.ID).toString().equals("0")) {
            auditLogList.forEach(auditLog -> auditLog.setOperationType(AuditOperationType.UPDATE.getName()));
        }
    }

    @Override
    public String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        WorkerGroup obj = workerGroupDao.queryById(objId);
        return obj == null ? "" : obj.getName();
    }
}
