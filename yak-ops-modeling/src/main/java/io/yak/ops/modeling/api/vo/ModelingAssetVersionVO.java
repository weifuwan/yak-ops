package io.yak.ops.modeling.api.vo;

import java.util.Date;
import lombok.Data;

/** Public version history item; serialized snapshot data is intentionally excluded. */
@Data
public class ModelingAssetVersionVO {

    private String id;
    private String sourceTableId;
    private String versionType;
    private Integer versionNumber;
    private Date createTime;
    private String createBy;
}
