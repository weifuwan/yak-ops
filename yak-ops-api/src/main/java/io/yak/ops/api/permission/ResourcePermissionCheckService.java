package io.yak.ops.api.permission;

import io.yak.ops.common.enums.AuthorizationType;

import java.util.Set;

import org.slf4j.Logger;

public interface ResourcePermissionCheckService<T> {

    /**
     * resourcePermissionCheck
     * @param authorizationType
     * @param needChecks
     * @param userId
     * @param logger
     * @return
     */
    boolean resourcePermissionCheck(Object authorizationType, Object[] needChecks, Integer userId, Logger logger);

    /**
     * userOwnedResourceIdsAcquisition
     * @param authorizationType
     * @param userId
     * @param logger
     * @return
     */
    Set<T> userOwnedResourceIdsAcquisition(Object authorizationType, Integer userId, Logger logger);

    /**
     * operationpermissionCheck
     * @param authorizationType
     * @param userId
     * @param permissionKey
     * @param logger
     * @return
     */
    boolean operationPermissionCheck(AuthorizationType authorizationType, Integer userId, String permissionKey,
                                     Logger logger);

}
