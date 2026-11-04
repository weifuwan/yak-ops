package io.yak.ops.api.utils;

import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.User;

import java.nio.charset.StandardCharsets;
import java.util.Random;

public class ServiceTestUtil {

    public static String randomStringWithLengthN(int n) {
        byte[] bitArray = new byte[n];
        new Random().nextBytes(bitArray);
        return new String(bitArray, StandardCharsets.UTF_8);
    }

    private static User getUser(Integer userId, String userName, UserType userType) {
        User user = new User();
        user.setUserType(userType);
        user.setId(userId);
        user.setUserName(userName);
        return user;
    }

    public static User getAdminUser() {
        return getUser(1, "admin", UserType.ADMIN_USER);
    }
    public static User getGeneralUser() {
        return getUser(10, "user", UserType.GENERAL_USER);
    }
}
