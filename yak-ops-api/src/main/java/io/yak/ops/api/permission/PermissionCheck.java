package io.yak.ops.api.permission;

import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.User;
import io.yak.ops.service.exceptions.ServiceException;
import io.yak.ops.service.process.ProcessService;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import org.slf4j.Logger;

import lombok.Getter;
import lombok.Setter;

public class PermissionCheck<T> {

    private Logger logger;

    private AuthorizationType authorizationType;

    /**
     * Authorization Type
     */
    @Getter
    @Setter
    private ProcessService processService;

    /**
     * need check array
     */
    private T[] needChecks;

    @Getter
    @Setter
    private int userId;

    public PermissionCheck(AuthorizationType authorizationType, ProcessService processService, T[] needChecks,
                           int userId, Logger logger) {
        this.authorizationType = authorizationType;
        this.processService = processService;
        this.needChecks = needChecks;
        this.userId = userId;
        this.logger = logger;
    }

    /**
     * check permission
     *
     * @throws ServiceException exception
     */
    public void checkPermission() throws ServiceException {
        if (this.needChecks.length > 0) {

            // get user type in order to judge whether the user is admin
            User user = processService.getUserById(userId);
            if (user == null) {
                logger.error("User does not exist, userId:{}.", userId);
                throw new ServiceException(String.format("user %s doesn't exist", userId));
            }
            if (user.getUserType() != UserType.ADMIN_USER) {
                List<T> unauthorizedList = processService.listUnauthorized(userId, needChecks, authorizationType);
                // if exist unauthorized resource
                if (CollectionUtils.isNotEmpty(unauthorizedList)) {
                    logger.error("User does not have {} permission for {}, userName:{}.",
                            authorizationType.getDescp(), unauthorizedList, user.getUserName());
                    throw new ServiceException(String.format("user %s doesn't have permission of %s %s",
                            user.getUserName(), authorizationType.getDescp(), unauthorizedList.get(0)));
                }
            }
        }
    }

}
