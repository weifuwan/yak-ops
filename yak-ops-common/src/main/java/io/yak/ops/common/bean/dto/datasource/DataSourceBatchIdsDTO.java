package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 数据源批量操作 ID 请求。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Data
public class DataSourceBatchIdsDTO {

    /** 单次批量操作允许的数据源数量上限。 */
    public static final int MAX_IDS = 100;

    /** 待操作的数据源 ID。 */
    @NotEmpty(message = "数据源 ID 列表不能为空")
    @Size(max = MAX_IDS, message = "单次批量操作最多支持 100 个数据源")
    private List<@NotBlank(message = "数据源 ID 不能为空") String> ids;
}
