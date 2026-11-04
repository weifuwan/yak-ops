package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.Cluster;
import io.yak.ops.dao.repository.ClusterDao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ClusterAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private ClusterDao clusterDao;

    @Override
    public String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        Cluster obj = clusterDao.queryByClusterCode(objId);
        return obj == null ? "" : obj.getName();
    }
}
