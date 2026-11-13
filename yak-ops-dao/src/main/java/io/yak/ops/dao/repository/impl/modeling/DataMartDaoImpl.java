package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.DataMartPO;
import io.yak.ops.dao.mapper.DataMartMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DataMartDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DataMartDaoImpl extends BaseDao<DataMartPO, DataMartMapper> implements DataMartDao {

    public DataMartDaoImpl(@NonNull DataMartMapper mapper) {
        super(mapper);
    }
}
