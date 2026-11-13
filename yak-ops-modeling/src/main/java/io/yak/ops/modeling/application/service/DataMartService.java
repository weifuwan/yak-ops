package io.yak.ops.modeling.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.modeling.api.dto.datamart.DataMartDTO;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;

import java.util.List;

/** Service contract for data marts. */
public interface DataMartService {

    String create(DataMartDTO dto);

    String create(DataMartDTO dto, String operator);

    DataMartVO updateById(DataMartDTO dto);

    DataMartVO updateById(DataMartDTO dto, String operator);

    String delete(String id);

    DataMartVO selectById(String id);

    IPage<DataMartVO> paging(DataMartDTO dto);

    List<TreeVO> tree(String keyword);

    List<DataMartVO> all();
}
