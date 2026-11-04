package io.yak.ops.plugin.storage.api;

import io.yak.ops.spi.enums.ResourceType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceMetadata {

    private String resourceAbsolutePath;

    private String resourceBaseDirectory;
    private String tenant;
    private ResourceType resourceType;
    private String resourceRelativePath;
    private String resourceParentAbsolutePath;
    private boolean isDirectory;

}
