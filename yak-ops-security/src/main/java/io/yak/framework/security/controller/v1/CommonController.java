package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.security.common.constant.Constants;
import io.yak.framework.security.web.PublicEndpoint;

import io.yak.framework.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = Constants.SWAGGER_API_TAG_PREFIX + "公共接口")
@RestController
@RequestMapping(value = {"/yak-security/api/v1/common"})
public class CommonController {
  @Operation(summary = "健康检查")
  @GetMapping(value = {"/heart"})
  @PublicEndpoint
  public Result<String> health() {
    return Result.success("\u4e00\u4e2a\u666e\u901a\u7684\u8bf7\u6c42\u54cd" +
                          "\u5e94\u4e86\u666e\u901a\u7684\u7ed3\u679c");
  }
}
