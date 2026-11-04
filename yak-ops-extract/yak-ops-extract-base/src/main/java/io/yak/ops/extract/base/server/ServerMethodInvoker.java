package io.yak.ops.extract.base.server;

interface ServerMethodInvoker {

    String getMethodIdentify();

    String getMethodProviderIdentify();

    Object invoke(final Object... arg) throws Throwable;

    boolean isParameterTypeValidated(Class<?>[] argsTypes);
}
