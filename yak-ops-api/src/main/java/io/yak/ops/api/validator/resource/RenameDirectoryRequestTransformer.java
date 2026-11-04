package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.RenameDirectoryDto;
import io.yak.ops.api.dto.resources.RenameDirectoryRequest;
import io.yak.ops.api.validator.ITransformer;

import org.apache.commons.lang3.StringUtils;

import java.io.File;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RenameDirectoryRequestTransformer implements ITransformer<RenameDirectoryRequest, RenameDirectoryDto> {

    @Override
    public RenameDirectoryDto transform(RenameDirectoryRequest renameDirectoryRequest) {
        String originDirectoryAbsolutePath = renameDirectoryRequest.getDirectoryAbsolutePath();
        String targetDirectoryName = renameDirectoryRequest.getNewDirectoryName();

        String targetDirectoryAbsolutePath =
                getTargetDirectoryAbsolutePath(originDirectoryAbsolutePath, targetDirectoryName);

        return RenameDirectoryDto.builder()
                .loginUser(renameDirectoryRequest.getLoginUser())
                .originDirectoryAbsolutePath(originDirectoryAbsolutePath)
                .targetDirectoryAbsolutePath(targetDirectoryAbsolutePath)
                .build();
    }

    private String getTargetDirectoryAbsolutePath(String originDirectoryAbsolutePath, String targetDirectoryName) {
        String originDirectoryParentAbsolutePath = StringUtils.substringBeforeLast(
                originDirectoryAbsolutePath, File.separator);
        return originDirectoryParentAbsolutePath + File.separator + targetDirectoryName;
    }
}
