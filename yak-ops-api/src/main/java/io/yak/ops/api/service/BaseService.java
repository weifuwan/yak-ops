package io.yak.ops.api.service;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.dao.entity.User;

import java.util.Date;

public interface BaseService {

    /**
     * check admin
     *
     * @param user input user
     * @return ture if administrator, otherwise return false
     */
    boolean isAdmin(User user);

    /**
     * isNotAdmin
     *
     * @param loginUser login user
     * @param result result code
     * @return true if not administrator, otherwise false
     */
    boolean isNotAdmin(User loginUser);

    /**
     * put message to result object
     *
     * @param result result code
     * @param status status
     * @param statusParams status message
     */
    void putMsg(Result<Object> result, Status status, Object... statusParams);

    /**
     * Verify that the operator has permissions
     *
     * @param operateUser operate user
     * @param createUserId create user id
     * @return check result
     */
    boolean canOperator(User operateUser, int createUserId);

    /**
     * Verify that the operator has permissions
     * @param user operate user
     * @param ids Object[]
     * @Param type authorizationType
     * @Param perm String
     * @return check result
     */
    boolean canOperatorPermissions(User user, Object[] ids, AuthorizationType type, String perm);

    /**
     * check and parse date parameters
     */
    Date checkAndParseDateParameters(String startDateStr) throws ServiceException;

    /**
     * check checkDescriptionLength
     *
     * @param description input String
     * @return ture if Length acceptable, Length exceeds return false
     */
    boolean checkDescriptionLength(String description);

}
