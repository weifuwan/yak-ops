package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Modeling asset persistence entity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yak_ops_dictionary_standard")
public class DictionaryStandardPO extends ModelingBasePO {
    private String enShortName;
            private String enName;
            private String cnName;
}
