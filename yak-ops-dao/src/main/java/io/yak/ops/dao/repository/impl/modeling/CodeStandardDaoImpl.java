package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.CodeStandardPO;
import io.yak.ops.dao.mapper.CodeStandardMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.CodeStandardDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class CodeStandardDaoImpl extends BaseDao<CodeStandardPO, CodeStandardMapper> implements CodeStandardDao {

    public CodeStandardDaoImpl(@NonNull CodeStandardMapper mapper) {
        super(mapper);
    }
}
