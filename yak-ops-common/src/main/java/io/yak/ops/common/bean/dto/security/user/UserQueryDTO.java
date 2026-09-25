package io.yak.ops.common.bean.dto.security.user;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询参数。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageQueryDTO {

    /** 用户 ID 精确筛选。 */
    private String id;

    /** 用户账号模糊筛选。 */
    private String userName;

    /** 用户真实姓名模糊筛选。 */
    private String realName;
}
