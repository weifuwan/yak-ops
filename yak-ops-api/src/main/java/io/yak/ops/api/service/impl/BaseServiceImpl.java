package io.yak.ops.api.service.impl;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.permission.ResourcePermissionCheckService;
import io.yak.ops.api.service.BaseService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.entity.User;

import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;
import java.util.Date;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BaseServiceImpl implements BaseService {

    @Autowired
    protected ResourcePermissionCheckService resourcePermissionCheckService;

    /**
     * check admin
     *
     * @param user input user
     * @return ture if administrator, otherwise return false
     */
    @Override
    public boolean isAdmin(User user) {
        return user.getUserType() == UserType.ADMIN_USER;
    }

    /**
     * isNotAdmin
     *
     * @param loginUser login user
     * @return true if not administrator, otherwise false
     */
    @Override
    public boolean isNotAdmin(User loginUser) {
        // only admin can operate
        return !isAdmin(loginUser);
    }

    /**
     * put message to result object
     *
     * @param result result code
     * @param status status
     * @param statusParams status message
     */
    @Override
    public void putMsg(Result result, Status status, Object... statusParams) {
        result.setCode(status.getCode());
        if (statusParams != null && statusParams.length > 0) {
            result.setMsg(MessageFormat.format(status.getMsg(), statusParams));
        } else {
            result.setMsg(status.getMsg());
        }
    }

    /**
     * Verify that the operator has permissions
     *
     * @param operateUser operate user
     * @param createUserId create user id
     */
    @Override
    public boolean canOperator(User operateUser, int createUserId) {
        return operateUser.getId() == createUserId || isAdmin(operateUser);
    }

    /**
     * Verify that the operator has permissions
     * @param user operate user
     * @param ids Object[]
     * @param type AuthorizationType
     * @return boolean
     */
    @Override
    public boolean canOperatorPermissions(User user, Object[] ids, AuthorizationType type, String permissionKey) {
        boolean operationPermissionCheck =
                resourcePermissionCheckService.operationPermissionCheck(type, user.getId(), permissionKey, log);
        boolean resourcePermissionCheck = resourcePermissionCheckService.resourcePermissionCheck(type, ids,
                user.getUserType().equals(UserType.ADMIN_USER) ? 0 : user.getId(), log);
        return operationPermissionCheck && resourcePermissionCheck;
    }

    /**
     * check and parse date parameters
     */
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

    @Override
    public boolean checkDescriptionLength(String description) {
        return description != null && description.codePointCount(0, description.length()) > 255;
    }

    protected Date transformDate(String dateStr) {
        Date date = DateUtils.stringToDate(dateStr);
        if (date == null) {
            throw new IllegalArgumentException("dateStr: [" + dateStr + "] is invalid");
        }
        return date;
    }
}
