package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.UpdateFileDto;
import io.yak.ops.api.dto.resources.UpdateFileRequest;
import io.yak.ops.api.validator.ITransformer;

import org.springframework.stereotype.Component;

@Component
public class UpdateFileRequestTransformer implements ITransformer<UpdateFileRequest, UpdateFileDto> {

    @Override
    public UpdateFileDto transform(UpdateFileRequest updateFileRequest) {
        return UpdateFileDto.builder()
                .loginUser(updateFileRequest.getLoginUser())
                .fileAbsolutePath(updateFileRequest.getFileAbsolutePath())
                .file(updateFileRequest.getFile())
                .build();
    }

}
