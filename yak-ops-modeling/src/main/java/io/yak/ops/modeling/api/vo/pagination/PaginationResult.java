package io.yak.ops.modeling.api.vo.pagination;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.yak.ops.modeling.api.dto.pagination.PaginationBaseDTO;
import lombok.Data;

/**
 * Response shape retained for compatibility with the original data_modal pages.
 */
@Data
public class PaginationResult<T> {

    private Integer code = 0;
    private String message = "success";
    private PaginationData<T> data;

    public PaginationResult() {
        this.data = new PaginationData<>();
        this.data.setBizData(Collections.emptyList());
        this.data.setPagination(new Pagination(0, 1, 10));
    }

    public static <T> PaginationResult<T> buildSuc(PaginationBaseDTO dto) {
        PaginationResult<T> result = new PaginationResult<>();
        if (dto != null) {
            result.data.setPagination(new Pagination(0, dto.getPageNo(), dto.getPageSize()));
        }
        return result;
    }

    public static <T, U> PaginationResult<T> buildSuc(List<T> records, IPage<U> page) {
        return buildSuc(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static <T> PaginationResult<T> buildSuc(List<T> records, long total, long pageNo, long pageSize) {
        PaginationResult<T> result = new PaginationResult<>();
        PaginationData<T> data = new PaginationData<>();
        data.setBizData(records == null ? new ArrayList<>() : records);
        data.setPagination(new Pagination(total, pageNo, pageSize));
        result.setData(data);
        return result;
    }

    public boolean hasData() {
        return data != null && data.getBizData() != null && !data.getBizData().isEmpty();
    }
}
