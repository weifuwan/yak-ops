package io.yak.ops.api.dto.resources;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public abstract class AbstractResourceDto {

    private String resourceAbsolutePath;

}
