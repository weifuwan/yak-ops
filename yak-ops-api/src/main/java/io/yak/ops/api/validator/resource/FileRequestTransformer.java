package io.yak.ops.api.validator.resource;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.api.dto.resources.CreateFileDto;
import io.yak.ops.api.dto.resources.CreateFileRequest;
import io.yak.ops.common.utils.FileUtils;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.springframework.stereotype.Component;

@Component
public class FileRequestTransformer extends AbstractResourceTransformer<CreateFileRequest, CreateFileDto> {

    public FileRequestTransformer(TenantDao tenantDao, StorageOperator storageOperator) {
        super(tenantDao, storageOperator);
    }

    @Override
    public CreateFileDto transform(CreateFileRequest createFileRequest) {
        validateCreateFileRequest(createFileRequest);
        return doTransform(createFileRequest);
    }

    private void validateCreateFileRequest(CreateFileRequest createFileRequest) {
        checkNotNull(createFileRequest.getLoginUser(), "loginUser is null");
        checkNotNull(createFileRequest.getType(), "resource type is null");
        checkNotNull(createFileRequest.getFileName(), "file name is null");
        checkNotNull(createFileRequest.getParentAbsoluteDirectory(), "parent directory is null");
        checkNotNull(createFileRequest.getFile(), "file is null");
    }

    private CreateFileDto doTransform(CreateFileRequest createFileRequest) {
        String fileAbsolutePath = getFileAbsolutePath(createFileRequest);
        return CreateFileDto.builder()
                .loginUser(createFileRequest.getLoginUser())
                .file(createFileRequest.getFile())
                .fileAbsolutePath(fileAbsolutePath)
                .build();

    }

    private String getFileAbsolutePath(CreateFileRequest createFileRequest) {
        String parentDirectoryAbsolutePath = getParentDirectoryAbsolutePath(
                createFileRequest.getLoginUser(),
                createFileRequest.getParentAbsoluteDirectory(),
                createFileRequest.getType());
        return FileUtils.concatFilePath(parentDirectoryAbsolutePath, createFileRequest.getFileName());
    }
}
