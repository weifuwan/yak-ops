package io.yak.ops.api.validator.resource;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.api.dto.resources.CreateFileFromContentDto;
import io.yak.ops.api.dto.resources.CreateFileFromContentRequest;
import io.yak.ops.common.utils.FileUtils;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.springframework.stereotype.Component;

@Component
public class FileFromContentRequestTransformer
        extends
            AbstractResourceTransformer<CreateFileFromContentRequest, CreateFileFromContentDto> {

    public FileFromContentRequestTransformer(TenantDao tenantDao, StorageOperator storageOperator) {
        super(tenantDao, storageOperator);
    }

    @Override
    public CreateFileFromContentDto transform(CreateFileFromContentRequest createFileFromContentRequest) {
        validateCreateFileRequest(createFileFromContentRequest);
        return doTransform(createFileFromContentRequest);
    }

    private void validateCreateFileRequest(CreateFileFromContentRequest createFileFromContentRequest) {
        checkNotNull(createFileFromContentRequest.getLoginUser(), "loginUser is null");
        checkNotNull(createFileFromContentRequest.getType(), "resource type is null");
        checkNotNull(createFileFromContentRequest.getFileName(), "file name is null");
        checkNotNull(createFileFromContentRequest.getParentAbsoluteDirectory(), "parent directory is null");
        checkNotNull(createFileFromContentRequest.getFileContent(), "file content is null");
    }

    private CreateFileFromContentDto doTransform(CreateFileFromContentRequest createFileFromContentRequest) {
        String fileAbsolutePath = getFileAbsolutePath(createFileFromContentRequest);
        return CreateFileFromContentDto.builder()
                .loginUser(createFileFromContentRequest.getLoginUser())
                .fileAbsolutePath(fileAbsolutePath)
                .fileContent(createFileFromContentRequest.getFileContent())
                .build();

    }

    private String getFileAbsolutePath(CreateFileFromContentRequest createFileFromContentRequest) {
        String parentDirectoryAbsolutePath = getParentDirectoryAbsolutePath(
                createFileFromContentRequest.getLoginUser(),
                createFileFromContentRequest.getParentAbsoluteDirectory(),
                createFileFromContentRequest.getType());
        return FileUtils.concatFilePath(parentDirectoryAbsolutePath, createFileFromContentRequest.getFileName());
    }

}
