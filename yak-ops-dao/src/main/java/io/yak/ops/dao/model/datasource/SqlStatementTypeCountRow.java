package io.yak.ops.dao.model.datasource;

import lombok.Getter;
import lombok.Setter;

/**
 * 承载 Statement 类型分布统计的数据库聚合结果。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Getter
@Setter
public class SqlStatementTypeCountRow {

    /** Statement 语义类型，保存业务枚举名称。 */
    private String statementType;

    /** 对应 Statement 类型的记录数量。 */
    private long count;
}
