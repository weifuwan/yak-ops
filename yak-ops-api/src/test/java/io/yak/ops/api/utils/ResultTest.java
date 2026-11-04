package io.yak.ops.api.utils;

import io.yak.ops.api.enums.Status;

import java.util.HashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResultTest {

    @Test
    public void success() {
        HashMap<String, String> map = new HashMap<>();
        map.put("testdata", "test");
        Result ret = Result.success(map);
        Assertions.assertEquals(Status.SUCCESS.getCode(), ret.getCode().intValue());
    }

    @Test
    public void error() {
        Result ret = Result.error(Status.ACCESS_TOKEN_NOT_EXIST);
        Assertions.assertEquals(Status.ACCESS_TOKEN_NOT_EXIST.getCode(), ret.getCode().intValue());
    }

    @Test
    public void errorWithArgs() {
        Result ret = Result.errorWithArgs(Status.INTERNAL_SERVER_ERROR_ARGS, "test internal server error");
        Assertions.assertEquals(Status.INTERNAL_SERVER_ERROR_ARGS.getCode(), ret.getCode().intValue());
    }
}
