package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.AlertGroup;
import io.yak.ops.dao.repository.AlertGroupDao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AlertGroupAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private AlertGroupDao alertGroupDao;

    @Override
    public String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        AlertGroup obj = alertGroupDao.queryById(objId);
        return obj == null ? "" : obj.getGroupName();
    }
}
