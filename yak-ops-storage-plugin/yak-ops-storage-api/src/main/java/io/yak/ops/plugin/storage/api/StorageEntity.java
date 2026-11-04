package io.yak.ops.plugin.storage.api;

import io.yak.ops.spi.enums.ResourceType;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// StorageEntity is an entity representing a resource in the third-part storage service.
// It is only stored in t_ds_relation_resources_task if the resource is used by a task.
// It is not put in the model module because it has more attributes than corresponding objects stored
//  in table t_ds_relation_resources_task.

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
