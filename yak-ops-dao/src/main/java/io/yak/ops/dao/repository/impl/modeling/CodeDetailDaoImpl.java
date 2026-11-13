package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.CodeDetailPO;
import io.yak.ops.dao.mapper.CodeDetailMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.CodeDetailDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class CodeDetailDaoImpl extends BaseDao<CodeDetailPO, CodeDetailMapper> implements CodeDetailDao {

    public CodeDetailDaoImpl(@NonNull CodeDetailMapper mapper) {
        super(mapper);
    }
}
