package io.yak.ops.common.bean.dto.security.user;

import io.yak.ops.common.bean.dto.security.PageParamDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户查询数据传输对象。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageParamDTO {
    private Long id;
    private String userName;
    private String realName;
}
