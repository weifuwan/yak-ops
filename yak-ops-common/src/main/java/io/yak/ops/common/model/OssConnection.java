package io.yak.ops.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OssConnection {

    public String accessKeyId;
    public String accessKeySecret;
    public String endPoint;

}
