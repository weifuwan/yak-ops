package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

/**
 * 数据源结构化连接参数。
 *
 * <p>HTTP 层只传递结构化连接信息；JDBC URL 生成、Provider 参数解释与规范化由数据源插件负责。</p>
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Data
public class DataSourceConnectionDTO {

    /** 数据库主机名或 IP。 */
    @NotBlank(message = "数据库主机不能为空")
    private String host;

    /** 数据库端口。 */
    @NotNull(message = "数据库端口不能为空")
    @Min(value = 1, message = "数据库端口必须大于 0")
    @Max(value = 65535, message = "数据库端口不能超过 65535")
    private Integer port;

    /** 默认数据库或服务名。 */
    @NotBlank(message = "数据库名称不能为空")
    private String database;

    /** 数据库登录用户名。 */
    @NotBlank(message = "数据库用户名不能为空")
    private String username;

    /** 数据库登录密码；编辑时允许提交遮罩值，由后端保留已保存密钥。 */
    private String password;

    /** Provider 自己解释的 JDBC Driver 选择标识；当前仅 MySQL 使用。 */
    private String driverId;

    /** 透传给对应 Provider 的高级连接属性。 */
    private Map<String, String> properties;
}
