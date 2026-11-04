package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.RenameFileDto;
import io.yak.ops.api.dto.resources.RenameFileRequest;
import io.yak.ops.api.validator.ITransformer;
import io.yak.ops.common.utils.FileUtils;
import io.yak.ops.plugin.storage.api.ResourceMetadata;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RenameFileRequestTransformer implements ITransformer<RenameFileRequest, RenameFileDto> {

    @Autowired
    private StorageOperator storageOperator;

    @Override
    public RenameFileDto transform(RenameFileRequest renameFileRequest) {
        ResourceMetadata resourceMetaData =
                storageOperator.getResourceMetaData(renameFileRequest.getFileAbsolutePath());
        return RenameFileDto.builder()
                .loginUser(renameFileRequest.getLoginUser())
                .originFileAbsolutePath(renameFileRequest.getFileAbsolutePath())
                .targetFileAbsolutePath(FileUtils.concatFilePath(resourceMetaData.getResourceParentAbsolutePath(),
                        renameFileRequest.getNewFileName()))
                .build();
    }
}
