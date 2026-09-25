package io.yak.ops.security.service;

import io.yak.ops.common.bean.dto.security.account.AccountLoginDTO;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.exception.YakSecurityException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 提供用户登录、退出和受保护请求登录态校验能力。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface LoginService {

    /**
     * 校验登录凭证并为当前请求建立登录态。
     *
     * @param loginDTO 登录凭证
     * @param request 当前 HTTP 请求
     * @return 登录成功后的用户摘要
     * @throws YakSecurityException 凭证无效、账号不可用或登录被临时锁定
     */
    UserBriefVO verifyLogin(AccountLoginDTO loginDTO, HttpServletRequest request) throws YakSecurityException;

    /** 退出当前登录态。 */
    Result<Boolean> logout();

    /**
     * 对请求路径执行公开路径匹配和登录态校验；未认证时直接写入 401 响应。
     *
     * @param response HTTP 响应
     * @param requestPath 当前请求路径
     * @param whiteListPatterns 公开路径模式
     * @return 允许继续处理返回 true
     * @throws IOException 写入响应失败
     */
    boolean interceptorCheck(HttpServletResponse response, String requestPath, List<String> whiteListPatterns)
            throws IOException;
}
