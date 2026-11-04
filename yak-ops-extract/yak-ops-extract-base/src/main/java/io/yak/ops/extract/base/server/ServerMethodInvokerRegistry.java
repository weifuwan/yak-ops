package io.yak.ops.extract.base.server;

interface ServerMethodInvokerRegistry {

    /**
     * Register service object, which will be used to invoke the {@link ServerMethodInvoker}.
     * The serverMethodInvokerProviderObject should implement with interface which contains {@link io.yak.ops.extract.base.RpcService} annotation.
     */
    void registerServerMethodInvokerProvider(Object serverMethodInvokerProviderObject);

}
