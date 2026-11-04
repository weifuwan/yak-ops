package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.AlertPluginInstance;
import io.yak.ops.dao.mapper.AlertPluginInstanceMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AlertInstanceAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private AlertPluginInstanceMapper alertPluginInstanceMapper;

    @Override
    public String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        AlertPluginInstance obj = alertPluginInstanceMapper.selectById(objId);
        return obj == null ? "" : obj.getInstanceName();
    }
}
