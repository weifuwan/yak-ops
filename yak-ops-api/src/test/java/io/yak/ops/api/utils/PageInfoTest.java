package io.yak.ops.api.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Arrays;

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

    @Test
    public void testMapMybatisPageToPageInfo() {
        Page<String> page = new Page<>(2, 10, 21);
        page.setRecords(Arrays.asList("first", "second"));

        PageInfo<String> result = PageInfo.of(page);

        Assertions.assertEquals(Arrays.asList("first", "second"), result.getTotalList());
        Assertions.assertEquals(21, result.getTotal());
        Assertions.assertEquals(2, result.getCurrentPage());
        Assertions.assertEquals(10, result.getPageNo());
        Assertions.assertEquals(3, result.getTotalPage());
    }
}
