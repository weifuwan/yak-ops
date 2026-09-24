package io.yak.ops.common.bean.dto.security.account;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
/**
 * 账号登录数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class AccountLoginDTO {
  /** 用户名。 */
  @NotBlank
  private String userName;
  /** 密码。 */
  @NotBlank
  private String pw;

}
