package io.yak.ops.api.security;

import io.yak.ops.api.utils.Result;
import io.yak.ops.dao.entity.User;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import lombok.NonNull;

public interface Authenticator {

    /**
     * Verifying legality via username and password
     *
     * @param username user name
     * @param password user password
     * @param ip       client ip
     * @return result object
     */
    Result<Map<String, String>> authenticate(@NonNull String username, String password, @NonNull String ip);

    /**
     * Get authenticated user
     * @param request http servlet request
     * @return user
     */
    User getAuthUser(HttpServletRequest request);
}
