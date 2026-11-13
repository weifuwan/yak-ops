package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.ModifierPO;
import io.yak.ops.dao.mapper.ModifierMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.ModifierDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class ModifierDaoImpl extends BaseDao<ModifierPO, ModifierMapper> implements ModifierDao {

    public ModifierDaoImpl(@NonNull ModifierMapper mapper) {
        super(mapper);
    }
}
