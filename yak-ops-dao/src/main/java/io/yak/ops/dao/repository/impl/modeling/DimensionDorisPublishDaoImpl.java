package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.common.context.ProjectCodeContext;
import io.yak.ops.dao.entity.DimensionDorisPublishPO;
import io.yak.ops.dao.mapper.DimensionDorisPublishMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DimensionDorisPublishDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class DimensionDorisPublishDaoImpl extends BaseDao<DimensionDorisPublishPO, DimensionDorisPublishMapper> implements DimensionDorisPublishDao {

    public DimensionDorisPublishDaoImpl(@NonNull DimensionDorisPublishMapper mapper) {
        super(mapper);
    }

    @Override
    public DimensionDorisPublishPO queryLatestSuccessful(
            String tableId, Integer datasourceId, String database, String table) {
        Long projectCode = ProjectCodeContext.getProjectCode();
        if (projectCode == null) {
            throw new IllegalStateException(
                    "Project context is required when querying Doris publish history");
        }
        return mybatisMapper.selectLatestSuccessful(
                tableId, datasourceId, database, table, projectCode);
    }
}
