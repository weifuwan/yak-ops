package io.yak.ops.modeling.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import java.util.List;

/** Common typed CRUD contract for one modeling page. */
public interface ModelingCrudService<D extends ModelingQueryDTO, V extends ModelingBaseVO> {

    IPage<V> page(D query);

    List<V> list(D query);

    V getById(String id);

    String create(D dto, String operator);

    V update(String id, D dto, String operator);

    String delete(String id);
}
