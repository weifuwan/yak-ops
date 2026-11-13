package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.VersionManagementPO;
import io.yak.ops.dao.mapper.VersionManagementMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.VersionManagementDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class VersionManagementDaoImpl extends BaseDao<VersionManagementPO, VersionManagementMapper> implements VersionManagementDao {

    public VersionManagementDaoImpl(@NonNull VersionManagementMapper mapper) {
        super(mapper);
    }
}
