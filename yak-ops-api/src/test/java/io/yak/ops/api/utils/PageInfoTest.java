package io.yak.ops.api.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

class PageInfoTest {

    @Test
    void shouldMapMybatisPageToApiPageInfo() {
        Page<String> page = new Page<>(2, 10, 21);
        page.setRecords(Arrays.asList("first", "second"));

        PageInfo<String> result = PageInfo.of(page);

        assertEquals(Arrays.asList("first", "second"), result.getTotalList());
        assertEquals(21, result.getTotal());
        assertEquals(2, result.getCurrentPage());
        assertEquals(10, result.getPageNo());
        assertEquals(3, result.getTotalPage());
    }
}
