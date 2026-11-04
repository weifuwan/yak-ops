package io.yak.ops.api.validator.resource;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.validator.ITransformer;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;
import io.yak.ops.spi.enums.ResourceType;

import org.apache.commons.lang3.StringUtils;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public abstract class AbstractResourceTransformer<T, R> implements ITransformer<T, R> {

    protected TenantDao tenantDao;

    protected StorageOperator storageOperator;

    protected String getParentDirectoryAbsolutePath(User loginUser, String parentAbsoluteDirectory, ResourceType type) {
        String tenantCode = tenantDao.queryOptionalById(loginUser.getTenantId())
                .orElseThrow(() -> new ServiceException(Status.CURRENT_LOGIN_USER_TENANT_NOT_EXIST))
                .getTenantCode();
        String userResRootPath = storageOperator.getStorageBaseDirectory(tenantCode, type);
        // If the parent directory is / then will transform to userResRootPath
        // This only happens when the front-end go into the resource page first
        // todo: we need to change the front-end logic to avoid this
        if (parentAbsoluteDirectory.equals("/")) {
            return userResRootPath;
        }

        if (!StringUtils.startsWith(parentAbsoluteDirectory, userResRootPath)) {
            throw new ServiceException(Status.ILLEGAL_RESOURCE_PATH, parentAbsoluteDirectory);
        }
        return parentAbsoluteDirectory;
    }
}
