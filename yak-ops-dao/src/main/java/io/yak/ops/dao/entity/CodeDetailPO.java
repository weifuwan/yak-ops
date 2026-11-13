package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Modeling asset persistence entity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yak_ops_code_detail")
public class CodeDetailPO extends ModelingBasePO {
    private String codeId;
            private String enShortName;
            private String enName;
            private String cnName;
            private String remark;
}
