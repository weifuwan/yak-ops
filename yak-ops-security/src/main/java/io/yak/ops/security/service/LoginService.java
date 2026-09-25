package io.yak.ops.security.service;

import io.yak.ops.common.bean.dto.security.account.AccountLoginDTO;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.ops.security.exception.YakSecurityException;
import io.yak.ops.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/** Login and authenticated-request contract owned by Yak Security. */
public interface LoginService {

    UserBriefVO verifyLogin(AccountLoginDTO loginDTO, HttpServletRequest request) throws YakSecurityException;

    Result<Boolean> logout();

    boolean interceptorCheck(HttpServletResponse response, String requestPath, List<String> whiteListPatterns)
            throws IOException;
}
