package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.Queue;
import io.yak.ops.dao.repository.QueueDao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class YarnQueueAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private QueueDao queueDao;

    @Override
    public String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        Queue obj = queueDao.queryById(objId);
        return obj == null ? "" : obj.getQueueName();
    }
}
