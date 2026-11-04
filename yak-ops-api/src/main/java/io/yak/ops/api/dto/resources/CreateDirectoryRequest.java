package io.yak.ops.api.dto.resources;

import io.yak.ops.dao.entity.User;
import io.yak.ops.spi.enums.ResourceType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDirectoryRequest {

    private User loginUser;

    private ResourceType type;

    private String parentAbsoluteDirectory;

    private String directoryName;
}
