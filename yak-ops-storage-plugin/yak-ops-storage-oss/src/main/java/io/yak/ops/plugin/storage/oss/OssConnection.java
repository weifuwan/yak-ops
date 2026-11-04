package io.yak.ops.plugin.storage.oss;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OssConnection {

    public String accessKeyId;
    public String accessKeySecret;
    public String endPoint;

}
