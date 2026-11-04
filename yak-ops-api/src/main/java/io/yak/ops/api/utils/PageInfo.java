package io.yak.ops.api.utils;

import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;

@Data
@Builder
@AllArgsConstructor
public class PageInfo<T> {

    private List<T> totalList = Collections.emptyList();
    private Integer total = 0;
    @Setter
    private Integer totalPage;
    private Integer pageSize = 20;
    private Integer currentPage = 0;
    private Integer pageNo;

    public PageInfo() {

    }

    public PageInfo(Integer currentPage, Integer pageSize) {
        if (currentPage == null) {
            currentPage = 1;
        }
        this.pageNo = (currentPage - 1) * pageSize;
        this.pageSize = pageSize;
        this.currentPage = currentPage;
    }

    public static <T> PageInfo<T> of(IPage<T> iPage) {
        PageInfo<T> pageInfo = new PageInfo<>((int) iPage.getCurrent(), (int) iPage.getSize());
        pageInfo.setTotalList(iPage.getRecords());
        pageInfo.setTotal((int) iPage.getTotal());
        return pageInfo;
    }

    public static <T> PageInfo<T> of(Integer currentPage, Integer pageSize) {
        return new PageInfo<>(currentPage, pageSize);
    }

    public Integer getTotalPage() {
        if (pageSize == null || pageSize == 0) {
            pageSize = 10;
        }
        this.totalPage =
                (this.total % this.pageSize) == 0
                        ? ((this.total / this.pageSize) == 0 ? 1 : (this.total / this.pageSize))
                        : (this.total / this.pageSize + 1);
        return this.totalPage;
    }
}
