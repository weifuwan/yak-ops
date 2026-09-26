package io.yak.ops.common.bean.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 Workspace 时使用的输入契约。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Data
public class WorkspaceDTO {

    /** Workspace 展示名称。 */
    @NotBlank(message = "工作空间名称不能为空")
    @Size(max = 128, message = "工作空间名称长度不能超过128个字符")
    private String name;

    /** Workspace 说明，可为空。 */
    @Size(max = 500, message = "工作空间描述长度不能超过500个字符")
    private String description;
}
