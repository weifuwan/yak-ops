package io.yak.ops.api.security.impl.sso;

import io.yak.ops.api.security.impl.AbstractSsoAuthenticator;
import io.yak.ops.api.service.UsersService;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.User;

import java.security.MessageDigest;

import javax.servlet.http.HttpServletRequest;

import org.casbin.casdoor.entity.CasdoorUser;
import org.casbin.casdoor.service.CasdoorAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.NonNull;

public class CasdoorAuthenticator extends AbstractSsoAuthenticator {

    @Autowired
    private UsersService usersService;
    @Autowired
    private CasdoorAuthService casdoorAuthService;
    @Value("${casdoor.redirect-url}")
    private String redirectUrl;
    @Value("${security.authentication.casdoor.user.admin:#{null}}")
    private String adminUserName;

    @Override
    public User login(@NonNull String userName, String code) {
        ServletRequestAttributes servletRequestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (servletRequestAttributes == null) {
            return null;
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        String originalState = (String) request.getSession().getAttribute(Constants.SSO_LOGIN_USER_STATE);
        // Invalid state
        request.getSession().setAttribute(Constants.SSO_LOGIN_USER_STATE, null);
        // Check state to protect from CSRF attack
        if (originalState == null || !MessageDigest.isEqual(originalState.getBytes(), userName.getBytes())) {
            return null;
        }

        String token = casdoorAuthService.getOAuthToken(code, userName);
        CasdoorUser casdoorUser = casdoorAuthService.parseJwtToken(token);
        User user = null;
        if (casdoorUser.getName() != null) {
            // check if user exist
            user = usersService.getUserByUserName(casdoorUser.getName());
            if (user == null) {
                user = usersService.createUser(getUserType(casdoorUser.getName()), casdoorUser.getName(),
                        casdoorUser.getEmail());
            }
        }
        return user;
    }

    public UserType getUserType(String userName) {
        return adminUserName.equalsIgnoreCase(userName) ? UserType.ADMIN_USER : UserType.GENERAL_USER;
    }

    @Override
    public String getSignInUrl(String state) {
        return casdoorAuthService.getSigninUrl(redirectUrl, state);
    }

}
