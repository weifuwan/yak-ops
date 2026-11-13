package io.yak.ops.dao.repository.modeling;

import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.repository.IDao;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface BusinessProcessDao extends IDao<BusinessProcessPO> {

    IPage<BusinessProcessPO> paging(
            IPage<BusinessProcessPO> page,
            String enShortName,
            String cnName,
            String enName,
            String dataDomainId,
            String director,
            String createBy);
}
