package io.yak.ops.extract.base.client;

import java.lang.reflect.Method;

interface ClientMethodInvoker {

    Object invoke(Object proxy, Method method, Object[] args) throws Throwable;

}
