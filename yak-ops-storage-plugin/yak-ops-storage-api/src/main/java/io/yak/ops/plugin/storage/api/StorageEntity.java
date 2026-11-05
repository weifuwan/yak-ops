package io.yak.ops.plugin.storage.api;

import io.yak.ops.spi.enums.ResourceType;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// StorageEntity represents a resource in a third-party storage service.
// It is separate from DAO resource models because it has additional storage attributes.

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageEntity {

    private String fullName;

    private String fileName;
    private String pfullName;
    private boolean isDirectory;
    private ResourceType type;
    private long size;
    private Date createTime;
    private Date updateTime;
    private String relativePath;
}
