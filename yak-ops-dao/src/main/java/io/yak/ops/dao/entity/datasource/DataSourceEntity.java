package io.yak.ops.dao.entity.datasource;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Persists Workspace-scoped datasource state in the yak_ops_data_source table.
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_data_source")
public class DataSourceEntity extends BaseEntity {

    /** Workspace identifier defining datasource ownership and isolation. */
    private String workspaceId;

    /** Datasource name, unique within the Workspace. */
    private String name;

    /** Database type used to select the matching datasource plugin. */
    private String dbType;

    /** JDBC connection URL. */
    private String jdbcUrl;

    /** Monotonic binding revision incremented on every datasource update. */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long revision;

    /** Environment assigned to the datasource. */
    private DataSourceEnvironment environment;

    /** Connectivity status observed during the latest connection test. */
    private DataSourceConnStatus connStatus;

    /** Optional user-maintained datasource description. */
    private String remark;

    /** Normalized JSON connection parameters, including sensitive credentials. */
    @ToString.Exclude
    private String connectionParams;

    /** Original JSON configuration used to populate the edit form; may contain credentials. */
    @ToString.Exclude
    private String originalJson;
}
