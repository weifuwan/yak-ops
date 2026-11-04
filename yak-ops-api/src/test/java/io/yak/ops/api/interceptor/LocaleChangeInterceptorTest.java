package io.yak.ops.api.interceptor;

import io.yak.ops.api.controller.AbstractControllerTest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

public class LocaleChangeInterceptorTest extends AbstractControllerTest {

    @Autowired
    LocaleChangeInterceptor interceptor;

    @Test
    public void testPreHandle() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        // test no language
        Assertions.assertTrue(interceptor.preHandle(request, response, null));
    }

}
