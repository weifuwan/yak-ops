package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.StandardDirectoryPO;
import io.yak.ops.dao.mapper.StandardDirectoryMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.StandardDirectoryDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class StandardDirectoryDaoImpl extends BaseDao<StandardDirectoryPO, StandardDirectoryMapper> implements StandardDirectoryDao {

    public StandardDirectoryDaoImpl(@NonNull StandardDirectoryMapper mapper) {
        super(mapper);
    }
}
