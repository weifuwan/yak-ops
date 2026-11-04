package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.Environment;
import io.yak.ops.dao.mapper.EnvironmentMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EnvironmentAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private EnvironmentMapper environmentMapper;

    @Override
    public String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        Environment obj = environmentMapper.queryByEnvironmentCode(objId);
        return obj == null ? "" : obj.getName();
    }
}
