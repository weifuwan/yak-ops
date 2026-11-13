package io.yak.ops.api.service.impl;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.permission.ResourcePermissionCheckService;
import io.yak.ops.api.service.BaseService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.common.service.BaseServiceImpl as SharedBaseServiceImpl;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.User;

import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;
import java.util.Date;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BaseServiceImpl extends SharedBaseServiceImpl implements BaseService {

    @Autowired
    protected ResourcePermissionCheckService resourcePermissionCheckService;

    @Override
    public boolean isAdmin(User user) {
        return user.getUserType() == UserType.ADMIN_USER;
    }

    @Override
    public boolean isNotAdmin(User loginUser) {
        return !isAdmin(loginUser);
    }

    @Override
    public void putMsg(Result result, Status status, Object... statusParams) {
        result.setCode(status.getCode());
        if (statusParams != null && statusParams.length > 0) {
            result.setMsg(MessageFormat.format(status.getMsg(), statusParams));
        } else {
            result.setMsg(status.getMsg());
        }
    }

    @Override
    public boolean canOperator(User operateUser, int createUserId) {
        return operateUser.getId() == createUserId || isAdmin(operateUser);
    }

    @Override
    public boolean canOperatorPermissions(User user, Object[] ids, AuthorizationType type, String permissionKey) {
        boolean operationPermissionCheck =
                resourcePermissionCheckService.operationPermissionCheck(type, user.getId(), permissionKey, log);
        boolean resourcePermissionCheck = resourcePermissionCheckService.resourcePermissionCheck(type, ids,
                user.getUserType().equals(UserType.ADMIN_USER) ? 0 : user.getId(), log);
        return operationPermissionCheck && resourcePermissionCheck;
    }

    @Override
    public Date checkAndParseDateParameters(String startDateStr) throws ServiceException {
        Date start = null;
        if (!StringUtils.isEmpty(startDateStr)) {
            start = DateUtils.stringToDate(startDateStr);
            if (Objects.isNull(start)) {
                log.warn("Parameter startDateStr is invalid.");
                throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, Constants.START_END_DATE);
            }
        }
        return start;
    }
}
