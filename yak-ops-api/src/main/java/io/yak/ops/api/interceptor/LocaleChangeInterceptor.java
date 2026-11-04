package io.yak.ops.api.interceptor;

import io.yak.ops.common.constants.Constants;

import java.util.Locale;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

public class LocaleChangeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Cookie cookie = WebUtils.getCookie(request, Constants.LOCALE_LANGUAGE);
        if (cookie != null) {
            // Proceed in cookie
            return true;
        }
        // Proceed in header
        String newLocale = request.getHeader(Constants.LOCALE_LANGUAGE);
        if (newLocale != null) {
            LocaleContextHolder.setLocale(parseLocaleValue(newLocale));
        }
        return true;
    }

    @Nullable
    protected Locale parseLocaleValue(String localeValue) {
        return StringUtils.parseLocale(localeValue);
    }

}
