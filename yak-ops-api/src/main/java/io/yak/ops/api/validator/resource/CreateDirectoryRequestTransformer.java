package io.yak.ops.api.validator.resource;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.api.dto.resources.CreateDirectoryDto;
import io.yak.ops.api.dto.resources.CreateDirectoryRequest;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.validator.ITransformer;
import io.yak.ops.common.utils.FileUtils;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CreateDirectoryRequestTransformer implements ITransformer<CreateDirectoryRequest, CreateDirectoryDto> {

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private StorageOperator storageOperator;

    @Override
    public CreateDirectoryDto transform(CreateDirectoryRequest createDirectoryRequest) {
        validateCreateDirectoryRequest(createDirectoryRequest);
        return doTransform(createDirectoryRequest);
    }

    private CreateDirectoryDto doTransform(CreateDirectoryRequest createDirectoryRequest) {
        String directoryAbsolutePath = getDirectoryAbsolutePath(createDirectoryRequest);
        return CreateDirectoryDto.builder()
                .loginUser(createDirectoryRequest.getLoginUser())
                .directoryAbsolutePath(directoryAbsolutePath)
                .build();
    }

    private void validateCreateDirectoryRequest(CreateDirectoryRequest createDirectoryRequest) {
        checkNotNull(createDirectoryRequest.getLoginUser(), "loginUser is null");
        checkNotNull(createDirectoryRequest.getType(), "resource type is null");
        checkNotNull(createDirectoryRequest.getDirectoryName(), "directory name is null");
        checkNotNull(createDirectoryRequest.getParentAbsoluteDirectory(), "parent directory is null");

    }

    private String getDirectoryAbsolutePath(CreateDirectoryRequest createDirectoryRequest) {
        String tenantCode = tenantDao.queryOptionalById(createDirectoryRequest.getLoginUser().getTenantId())
                .orElseThrow(() -> new ServiceException(Status.CURRENT_LOGIN_USER_TENANT_NOT_EXIST))
                .getTenantCode();
        String userResRootPath = storageOperator.getStorageBaseDirectory(tenantCode, createDirectoryRequest.getType());
        String parentDirectoryName = createDirectoryRequest.getParentAbsoluteDirectory();
        String directoryName = createDirectoryRequest.getDirectoryName();

        // If the parent directory is / then will transform to userResRootPath
        // This only happens when the front-end go into the resource page first
        // todo: we need to change the front-end logic to avoid this
        if (parentDirectoryName.equals("/")) {
            return FileUtils.concatFilePath(userResRootPath, directoryName);
        }

        if (!StringUtils.startsWith(parentDirectoryName, userResRootPath)) {
            throw new ServiceException(Status.ILLEGAL_RESOURCE_PATH, parentDirectoryName);
        }
        return FileUtils.concatFilePath(parentDirectoryName, directoryName);
    }
}
