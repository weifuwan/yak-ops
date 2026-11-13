package io.yak.ops.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.BusinessProcessPO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

public interface BusinessProcessMapper extends BaseMapper<BusinessProcessPO> {

    IPage<BusinessProcessPO> paging(
            IPage<BusinessProcessPO> page,
            @Param("enShortName") String enShortName,
            @Param("cnName") String cnName,
            @Param("enName") String enName,
            @Param("dataDomainId") String dataDomainId,
            @Param("director") String director,
            @Param("createBy") String createBy);
}
