package io.yak.ops.security.service.impl;

import io.yak.ops.common.Result;
import io.yak.ops.common.bean.dto.security.account.AccountLoginDTO;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.ops.security.extend.LoginExtend;
import io.yak.ops.security.service.LoginService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(
    prefix = "yak.security",
    name = {"enabled", "database-enabled"},
    havingValue = "true",
    matchIfMissing = true)
@Service("yakSecurityLoginServiceImpl")
public class LoginServiceImpl implements LoginService {

  private final LoginExtend loginExtend;

  public LoginServiceImpl(LoginExtend loginExtend) {
    this.loginExtend = Objects.requireNonNull(
            loginExtend,
            "loginExtend must not be null");
  }

  @Override
  public UserBriefVO verifyLogin(
          AccountLoginDTO loginDTO,
          HttpServletRequest request,
          HttpServletResponse response) {

    if (loginDTO == null) {
      throw new IllegalArgumentException(
              "登录信息不能为空");
    }

    if (request == null || response == null) {
      throw new IllegalArgumentException(
              "HTTP 请求和响应不能为空");
    }

    return loginExtend.verifyLogin(
            loginDTO,
            request,
            response);
  }

  @Override
  public Result<Boolean> logout(
          HttpServletRequest request,
          HttpServletResponse response) {

    if (request == null || response == null) {
      throw new IllegalArgumentException(
              "HTTP 请求和响应不能为空");
    }

    return loginExtend.logout(
            request,
            response);
  }

  @Override
  public boolean interceptorCheck(
          HttpServletRequest request,
          HttpServletResponse response,
          String requestMappingValue,
          List<String> whiteMappingValues)
          throws IOException {

    if (request == null || response == null) {
      throw new IllegalArgumentException(
              "HTTP 请求和响应不能为空");
    }

    List<String> safeWhiteMappingValues =
            whiteMappingValues == null
                    ? Collections.emptyList()
                    : whiteMappingValues;

    return loginExtend.interceptorCheck(
            request,
            response,
            requestMappingValue,
            safeWhiteMappingValues);
  }
}