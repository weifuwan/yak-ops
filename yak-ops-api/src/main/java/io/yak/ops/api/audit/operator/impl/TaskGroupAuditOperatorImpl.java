package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.TaskGroup;
import io.yak.ops.dao.mapper.TaskGroupMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskGroupAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private TaskGroupMapper taskGroupMapper;

    @Override
    public String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        TaskGroup obj = taskGroupMapper.selectById(objId);
        return obj == null ? "" : obj.getName();
    }
}
