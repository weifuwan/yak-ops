package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.constants.AuditLogConstants;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.AccessToken;
import io.yak.ops.dao.entity.AuditLog;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.AccessTokenDao;
import io.yak.ops.dao.repository.UserDao;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TokenAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private AccessTokenDao accessTokenDao;

    @Autowired
    private UserDao userDao;

    @Override
    public void modifyAuditOperationType(AuditType auditType, Map<String, Object> paramsMap,
                                         List<AuditLog> auditLogList) {
        if (paramsMap.get(AuditLogConstants.USER_ID) != null) {
            User user = userDao.queryById(paramsMap.get(AuditLogConstants.USER_ID).toString());
            auditLogList.forEach(auditLog -> {
                auditLog.setModelName(user.getUserName());
                auditLog.setModelId(Long.valueOf(user.getId()));
            });
        }
    }

    @Override
    public String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        AccessToken obj = accessTokenDao.queryById(objId);
        return obj == null ? "" : obj.getUserName();
    }
}
