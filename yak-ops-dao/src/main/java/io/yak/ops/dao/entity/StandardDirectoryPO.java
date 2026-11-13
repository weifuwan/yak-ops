package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Modeling asset persistence entity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yak_ops_standard_directory")
public class StandardDirectoryPO extends ModelingBasePO {
    private String dirName;
            private String dirType;
            private String dirModule;
            private String parentId;
}
