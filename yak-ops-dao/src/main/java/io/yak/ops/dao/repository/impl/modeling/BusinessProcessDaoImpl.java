package io.yak.ops.dao.repository.impl.modeling;

import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.mapper.BusinessProcessMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.BusinessProcessDao;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessProcessDaoImpl extends BaseDao<BusinessProcessPO, BusinessProcessMapper> implements BusinessProcessDao {

    public BusinessProcessDaoImpl(@NonNull BusinessProcessMapper mapper) {
        super(mapper);
    }

    @Override
    public IPage<BusinessProcessPO> paging(
            IPage<BusinessProcessPO> page,
            String enShortName,
            String cnName,
            String enName,
            String dataDomainId,
            String director,
            String createBy) {
        return mybatisMapper.paging(page, enShortName, cnName, enName, dataDomainId, director, createBy);
    }
}
