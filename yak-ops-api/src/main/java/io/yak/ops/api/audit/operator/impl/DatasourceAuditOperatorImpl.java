package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.repository.DataSourceDao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatasourceAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private DataSourceDao dataSourceDao;

    @Override
    public String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        DataSource obj = dataSourceDao.queryById(objId);
        return obj == null ? "" : obj.getName();
    }
}
