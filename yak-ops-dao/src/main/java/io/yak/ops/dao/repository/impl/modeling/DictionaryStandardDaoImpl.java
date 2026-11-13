package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.DictionaryStandardPO;
import io.yak.ops.dao.mapper.DictionaryStandardMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DictionaryStandardDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DictionaryStandardDaoImpl extends BaseDao<DictionaryStandardPO, DictionaryStandardMapper> implements DictionaryStandardDao {

    public DictionaryStandardDaoImpl(@NonNull DictionaryStandardMapper mapper) {
        super(mapper);
    }
}
