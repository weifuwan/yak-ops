package io.yak.ops.api.security.impl;

public abstract class AbstractSsoAuthenticator extends AbstractAuthenticator {

    public abstract String getSignInUrl(String state);
}
