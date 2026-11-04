package io.yak.ops.api.security.impl.ldap;

import io.yak.ops.api.dto.LdapLoginResult;
import io.yak.ops.api.security.impl.AbstractAuthenticator;
import io.yak.ops.dao.entity.User;

import org.springframework.beans.factory.annotation.Autowired;

import lombok.NonNull;

public class LdapAuthenticator extends AbstractAuthenticator {

    @Autowired
    LdapService ldapService;

    @Override
    public User login(@NonNull String userName, String password) {
        User user = null;
        LdapLoginResult ldapLoginResult = ldapService.ldapLogin(userName, password);
        if (ldapLoginResult.isSuccess()) {
            // check if user exist
            user = userService.getUserByUserName(userName);
            if (user == null && ldapService.createIfUserNotExists()) {
                user = userService.createUser(ldapLoginResult.getUserType(), userName, ldapLoginResult.getLdapEmail());
            }
        }
        return user;
    }
}
