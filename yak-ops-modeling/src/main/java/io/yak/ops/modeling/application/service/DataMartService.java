package io.yak.ops.modeling.application.service;


import io.yak.ops.modeling.api.dto.datamart.DataMartDTO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;

import java.util.List;

/**
 * 业务分类服务接口
 *
 * @author bruce
 */
public interface DataMartService {

    String create(DataMartDTO dto);

    DataMartVO updateById(DataMartDTO dto);

    String delete(String id);

    DataMartVO selectById(String id);

    PaginationResult<DataMartVO> paging(DataMartDTO dto);

    List<TreeVO> tree(String keyword);

    List<DataMartVO> all();
}
