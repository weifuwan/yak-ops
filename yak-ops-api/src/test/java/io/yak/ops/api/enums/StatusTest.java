package io.yak.ops.api.enums;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

public class StatusTest {

    @Test
    public void testGetCode() {
        Assertions.assertEquals(0, Status.SUCCESS.getCode());
        Assertions.assertNotEquals(0, Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode());
    }

    @Test
    public void testGetMsg() {
        LocaleContextHolder.setLocale(Locale.US);
        Assertions.assertEquals("success", Status.SUCCESS.getMsg());

        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        Assertions.assertEquals("成功", Status.SUCCESS.getMsg());

        LocaleContextHolder.setLocale(Locale.US);
    }

    @Test
    public void testGetStatusByCode() {
        // FAILURE
        Optional<Status> optional = Status.findStatusBy(1);
        Assertions.assertFalse(optional.isPresent());

        // SUCCESS
        optional = Status.findStatusBy(10018);
        Assertions.assertTrue(optional.isPresent());
        Assertions.assertEquals(Status.PROJECT_NOT_FOUND, optional.get());
    }
}
