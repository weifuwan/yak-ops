package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Modeling asset persistence entity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yak_ops_version_management")
public class VersionManagementPO extends ModelingBasePO {
    private String sourceTableId;
            private String versionData;
            private String versionType;
            private Integer versionNumber;
}
