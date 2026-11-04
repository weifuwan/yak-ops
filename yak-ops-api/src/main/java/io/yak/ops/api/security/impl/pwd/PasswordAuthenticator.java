package io.yak.ops.api.security.impl.pwd;

import io.yak.ops.api.security.impl.AbstractAuthenticator;
import io.yak.ops.dao.entity.User;

import lombok.NonNull;

public class PasswordAuthenticator extends AbstractAuthenticator {

    @Override
    public User login(@NonNull String userName, String password) {
        return userService.queryUser(userName, password);
    }
}
