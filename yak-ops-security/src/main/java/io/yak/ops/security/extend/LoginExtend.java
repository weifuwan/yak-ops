package io.yak.ops.security.extend;

import io.yak.ops.common.Result;
import io.yak.ops.common.bean.dto.security.account.AccountLoginDTO;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.ops.common.exception.YakSecurityException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * 登录认证扩展点。
 *
 * <p>自定义实现可以负责：</p>
 *
 * <ul>
 *   <li>账号密码校验；</li>
 *   <li>登录会话初始化；</li>
 *   <li>退出登录；</li>
 *   <li>请求登录状态拦截。</li>
 * </ul>
 *
 * @author weifuwan
 */
public interface LoginExtend {

  /**
   * 校验登录信息并建立登录会话。
   *
   * @param loginDTO 登录参数
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 当前登录用户简要信息
   * @throws YakSecurityException 登录校验失败时抛出
   */
  UserBriefVO verifyLogin(
          AccountLoginDTO loginDTO,
          HttpServletRequest request,
          HttpServletResponse response)
          throws YakSecurityException;

  /**
   * 退出登录并清理登录上下文。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 退出登录结果
   */
  Result<Boolean> logout(
          HttpServletRequest request,
          HttpServletResponse response);

  /**
   * 检查当前请求是否允许访问。
   *
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @param requestPath 当前请求路径
   * @param whiteListPatterns 白名单路径表达式
   * @return 允许访问返回 {@code true}
   * @throws IOException 响应写入异常
   */
  boolean interceptorCheck(
          HttpServletRequest request,
          HttpServletResponse response,
          String requestPath,
          List<String> whiteListPatterns)
          throws IOException;
}