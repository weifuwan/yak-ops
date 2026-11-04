package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.UpdateFileFromContentDto;
import io.yak.ops.api.dto.resources.UpdateFileFromContentRequest;
import io.yak.ops.api.validator.ITransformer;

import org.springframework.stereotype.Component;

@Component
public class UpdateFileFromContentRequestTransformer
        implements
            ITransformer<UpdateFileFromContentRequest, UpdateFileFromContentDto> {

    @Override
    public UpdateFileFromContentDto transform(UpdateFileFromContentRequest updateFileContentRequest) {
        return UpdateFileFromContentDto.builder()
                .loginUser(updateFileContentRequest.getLoginUser())
                .fileAbsolutePath(updateFileContentRequest.getFileAbsolutePath())
                .fileContent(updateFileContentRequest.getFileContent())
                .build();
    }
}
