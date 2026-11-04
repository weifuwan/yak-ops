package io.yak.ops.api.interceptor;

import io.yak.ops.api.configuration.ApiConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RateLimitInterceptorTest {

    @Test
    public void testPreHandleWithoutControl() throws ExecutionException {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        RateLimitInterceptor rateLimitInterceptor = new RateLimitInterceptor(new ApiConfig.TrafficConfiguration());
        Assertions.assertTrue(rateLimitInterceptor.preHandle(request, response, null));
        Assertions.assertTrue(rateLimitInterceptor.preHandle(request, response, null));
    }

    @Test
    public void testPreHandleWithTenantLevenControl() throws ExecutionException {
        ApiConfig.TrafficConfiguration trafficConfiguration = new ApiConfig.TrafficConfiguration();
        trafficConfiguration.setTenantSwitch(true);
        Map<String, Integer> map = new HashMap<>();
        map.put("tenant1", 2);
        map.put("tenant2", 2);
        trafficConfiguration.setCustomizeTenantQpsRate(map);
        trafficConfiguration.setDefaultTenantQpsRate(4);
        RateLimitInterceptor rateLimitInterceptor = new RateLimitInterceptor(trafficConfiguration);

        HttpServletRequest tenant1Request = Mockito.mock(HttpServletRequest.class);
        HttpServletRequest tenant2Request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(tenant1Request.getHeader(Mockito.any())).thenReturn("tenant1");
        Mockito.when(tenant2Request.getHeader(Mockito.any())).thenReturn("tenant2");
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);

        for (int i = 0; i < 2; i++) {
            rateLimitInterceptor.preHandle(tenant1Request, response, null);
        }
        Assertions.assertFalse(rateLimitInterceptor.preHandle(tenant1Request, response, null));
        Assertions.assertTrue(rateLimitInterceptor.preHandle(tenant2Request, response, null));
    }

    @Test
    public void testPreHandleWithGlobalControl() throws ExecutionException {
        ApiConfig.TrafficConfiguration trafficConfiguration = new ApiConfig.TrafficConfiguration();
        trafficConfiguration.setTenantSwitch(true);
        trafficConfiguration.setGlobalSwitch(true);
        trafficConfiguration.setMaxGlobalQpsRate(3);

        RateLimitInterceptor rateLimitInterceptor = new RateLimitInterceptor(trafficConfiguration);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);

        for (int i = 0; i < 2; i++) {
            rateLimitInterceptor.preHandle(request, response, null);
        }
        Assertions.assertFalse(rateLimitInterceptor.preHandle(request, response, null));
    }

}
