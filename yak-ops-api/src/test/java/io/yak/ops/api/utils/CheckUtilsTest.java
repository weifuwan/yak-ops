package io.yak.ops.api.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CheckUtilsTest {

    /**
     * check username
     */
    @Test
    public void testCheckUserName() {
        Assertions.assertTrue(CheckUtils.checkUserName("test01"));
        Assertions.assertFalse(CheckUtils.checkUserName(null));
        Assertions.assertFalse(CheckUtils.checkUserName("test01@abc"));
    }

    /**
     * check email
     */
    @Test
    public void testCheckEmail() {
        Assertions.assertTrue(CheckUtils.checkEmail("test01@gmail.com"));
        Assertions.assertFalse(CheckUtils.checkEmail("test01@gmail"));
        Assertions.assertFalse(CheckUtils.checkEmail("test01@gmail."));
        Assertions.assertTrue(CheckUtils.checkEmail("test01@gmail.edu.cn"));
    }

    @Test
    public void testCheckOtherParams() {
        Assertions.assertFalse(CheckUtils.checkOtherParams(null));
        Assertions.assertFalse(CheckUtils.checkOtherParams(""));
        Assertions.assertTrue(CheckUtils.checkOtherParams("xxx"));
        Assertions.assertFalse(CheckUtils.checkOtherParams("{}"));
        Assertions.assertFalse(CheckUtils.checkOtherParams("{\"key1\":111}"));
    }

    /**
     * check passwd
     */
    @Test
    public void testCheckPassword() {
        Assertions.assertFalse(CheckUtils.checkPassword(null));
        Assertions.assertFalse(CheckUtils.checkPassword("a"));
        Assertions.assertFalse(CheckUtils.checkPassword("1234567890abcderfasdf2"));
        Assertions.assertTrue(CheckUtils.checkPassword("123456"));
        Assertions.assertFalse(CheckUtils.checkPasswordLength("1"));
        Assertions.assertTrue(CheckUtils.checkPasswordLength("yak-ops123"));
        Assertions.assertTrue(CheckUtils.checkPasswordLength("yak-ops123456"));
        Assertions.assertFalse(CheckUtils.checkPasswordLength("123456789012345678901"));
    }

    /**
     * check phone
     */
    @Test
    public void testCheckPhone() {
        // phone can be null
        Assertions.assertTrue(CheckUtils.checkPhone(null));
        Assertions.assertFalse(CheckUtils.checkPhone("14567134578654"));
        Assertions.assertTrue(CheckUtils.checkPhone("17362537263"));
    }

}
