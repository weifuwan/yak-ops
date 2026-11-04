package io.yak.ops.extract.base.server;

import io.yak.ops.extract.base.config.NettyServerConfig;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.Nullable;

import lombok.extern.slf4j.Slf4j;

/**
 * The RpcServer which will auto discovery the {@link ServerMethodInvoker} from Spring container.
 */
@Slf4j
public class SpringServerMethodInvokerDiscovery extends RpcServer implements BeanPostProcessor {

    public SpringServerMethodInvokerDiscovery(NettyServerConfig nettyServerConfig) {
        super(nettyServerConfig);
    }

    @Nullable
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        registerServerMethodInvokerProvider(bean);
        return bean;
    }

}
