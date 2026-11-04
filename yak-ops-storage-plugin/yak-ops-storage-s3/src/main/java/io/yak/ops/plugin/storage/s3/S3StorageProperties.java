package io.yak.ops.plugin.storage.s3;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class S3StorageProperties {

    private Map<String, String> s3Configuration;

    private String bucketName;

    private String resourceUploadPath;
}
