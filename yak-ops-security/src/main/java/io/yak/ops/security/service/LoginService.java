package io.yak.ops.security.service;

import io.yak.framework.common.Result;
import io.yak.ops.security.common.dto.account.AccountLoginDTO;
import io.yak.ops.security.common.vo.user.UserBriefVO;
import io.yak.ops.security.exception.YakSecurityException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * 登录服务接口。
 *
 * @author weifuwan
 */
public interface LoginService {

  /**
   * 校验登录信息并返回当前用户。
   *
   * @param loginDTO 登录信息
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 当前登录用户
   * @throws YakSecurityException 登录校验失败
   */
  UserBriefVO verifyLogin(
          AccountLoginDTO loginDTO,
          HttpServletRequest request,
          HttpServletResponse response)
          throws YakSecurityException;

  /**
   * 退出登录。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 退出结果
   */
  Result<Boolean> logout(
          HttpServletRequest request,
          HttpServletResponse response);

  /**
   * 执行登录拦截校验。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @param requestMappingValue 当前请求路径
   * @param whiteMappingValues 白名单路径列表
   * @return 是否允许继续访问
   * @throws IOException 写入响应失败
   */
  boolean interceptorCheck(
          HttpServletRequest request,
          HttpServletResponse response,
          String requestMappingValue,
          List<String> whiteMappingValues)
          throws IOException;
}