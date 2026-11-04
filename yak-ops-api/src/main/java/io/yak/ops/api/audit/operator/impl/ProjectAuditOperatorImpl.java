package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.repository.ProjectDao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProjectAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private ProjectDao projectDao;

    @Override
    protected String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        Project obj = projectDao.queryByCode(objId);
        return obj == null ? "" : obj.getName();
    }
}
