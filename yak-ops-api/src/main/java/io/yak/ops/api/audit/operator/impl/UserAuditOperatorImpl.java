package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.UserDao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private UserDao userDao;

    @Override
    public String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        User obj = userDao.queryById(objId);
        return obj == null ? "" : obj.getUserName();
    }
}
