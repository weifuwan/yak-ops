package io.yak.ops.extract.base.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.google.common.collect.Lists;

class ServerMethodInvokerImpl implements ServerMethodInvoker {

    private final Object serviceBean;

    private final Method method;

    private final String methodIdentify;

    private final List<Class<?>> parameterTypes;

    ServerMethodInvokerImpl(Object serviceBean, Method method) {
        this.serviceBean = serviceBean;
        this.method = method;
        this.methodIdentify = method.toGenericString();
        this.parameterTypes = Lists.newArrayList(method.getParameterTypes());
    }

    @Override
    public Object invoke(Object... args) throws Throwable {
        // todo: check the request param when register
        try {
            return method.invoke(serviceBean, args);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }

    @Override
    public boolean isParameterTypeValidated(Class<?>[] argsTypes) {
        if (argsTypes == null || argsTypes.length == 0) {
            return parameterTypes.isEmpty();
        }
        if (parameterTypes.size() != argsTypes.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.size(); i++) {
            Class<?> argType = argsTypes[i];
            if (argType == null) {
                continue;
            }
            if (!parameterTypes.get(i).isAssignableFrom(argType)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getMethodIdentify() {
        return methodIdentify;
    }

    @Override
    public String getMethodProviderIdentify() {
        return serviceBean.getClass().getName();
    }
}
