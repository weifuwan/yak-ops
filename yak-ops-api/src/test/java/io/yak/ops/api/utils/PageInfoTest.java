package io.yak.ops.api.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PageInfoTest {

    @Test
    public void testGetTotalPageWhenTotalAndPageSizeArePositive() {
        PageInfo<Object> pageInfo = new PageInfo<>();
        pageInfo.setTotal(100);
        pageInfo.setPageSize(20);
        Assertions.assertEquals(5, pageInfo.getTotalPage());
    }

    @Test
    public void testGetTotalPageWhenTotalIsZero() {
        PageInfo<Object> pageInfo = new PageInfo<>();
        pageInfo.setTotal(0);
        pageInfo.setPageSize(20);
        Assertions.assertEquals(1, pageInfo.getTotalPage());
    }

    @Test
    public void testGetTotalPageWhenPageSizeIsZero() {
        PageInfo<Object> pageInfo = new PageInfo<>();
        pageInfo.setTotal(101);
        pageInfo.setPageSize(0);
        Assertions.assertEquals(11, pageInfo.getTotalPage());
    }
}
