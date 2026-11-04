package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.repository.TaskInstanceDao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskInstancesAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private TaskInstanceDao taskInstanceDao;

    @Override
    protected String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        TaskInstance obj = taskInstanceDao.queryById(objId);
        return obj == null ? "" : obj.getName();
    }
}
