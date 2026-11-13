package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.ComposeIndicatorPO;
import io.yak.ops.dao.mapper.ComposeIndicatorMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.ComposeIndicatorDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class ComposeIndicatorDaoImpl extends BaseDao<ComposeIndicatorPO, ComposeIndicatorMapper> implements ComposeIndicatorDao {

    public ComposeIndicatorDaoImpl(@NonNull ComposeIndicatorMapper mapper) {
        super(mapper);
    }
}
