package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.DownloadFileDto;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.springframework.stereotype.Component;

@Component
public class DownloadFileDtoValidator extends AbstractResourceValidator<DownloadFileDto> {

    public DownloadFileDtoValidator(StorageOperator storageOperator, TenantDao tenantDao) {
        super(storageOperator, tenantDao);
    }

    @Override
    public void validate(DownloadFileDto downloadFileDto) {
        String fileAbsolutePath = downloadFileDto.getFileAbsolutePath();
        User loginUser = downloadFileDto.getLoginUser();

        exceptionResourceNotExists(fileAbsolutePath);
        exceptionResourceAbsolutePathInvalidated(fileAbsolutePath);
        exceptionResourceIsNotFile(fileAbsolutePath);
        exceptionUserNoResourcePermission(loginUser, fileAbsolutePath);
    }
}
