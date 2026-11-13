package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.modeling.api.dto.datadomain.DataDomainDTO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.modeling.infrastructure.persistence.entity.datadomain.DataDomainPO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.application.service.BusinessProcessService;
import io.yak.ops.modeling.application.service.DataDomainService;
import io.yak.ops.modeling.infrastructure.persistence.mapper.DataDomainMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DataDomainServiceImpl extends ServiceImpl<DataDomainMapper, DataDomainPO> implements DataDomainService {

    @Resource
    private BusinessProcessService businessProcessService;

    @Override
    public String create(DataDomainDTO dto) {
        DataDomainPO po = ConvertUtil.sourceToTarget(dto, DataDomainPO.class);
        po.initInsert();
        po.setFlag(0);
        save(po);
        BusinessProcessDTO businessProcessDTO = BusinessProcessDTO.builder()
                .cnName(dto.getCnName() + "_" + "默认")
                .enName(dto.getEnName() + "_" + "default")
                .enShortName(dto.getEnShortName() + "_" + "default")
                .remark(String.format("%s下的默认业务过程", dto.getCnName()))
                .flag(1)
                .dataDomainId(po.getId())
                .build();
        // 同时需要新增默认业务过程, flag=1
        businessProcessService.create(businessProcessDTO);
        return po.getId();
    }

    @Override
    public DataDomainVO updateById(DataDomainDTO dto) {
        DataDomainPO po = ConvertUtil.sourceToTarget(dto, DataDomainPO.class);
        po.initUpdate();
        updateById(po);
        return ConvertUtil.sourceToTarget(po, DataDomainVO.class);
    }

    @Override
    @Transactional
    public String delete(String id) {
        removeById(id);
        // 同时删除业务过程
        businessProcessService.deleteByDomainId(id);
        return id;
    }

    @Override
    public DataDomainVO selectById(String id) {
        DataDomainPO po = getById(id);
        return ConvertUtil.sourceToTarget(po, DataDomainVO.class);
    }

    @Override
    public PaginationResult<DataDomainVO> paging(DataDomainDTO dto) {
        IPage<DataDomainPO> iPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<DataDomainPO> wrapper = new LambdaQueryWrapper<>();

        wrapper.orderByDesc(DataDomainPO::getUpdateTime);
        IPage<DataDomainPO> result = page(iPage, wrapper);
        List<DataDomainVO> records = ConvertUtil.sourceListToTarget(result.getRecords(), DataDomainVO.class);
        return PaginationResult.buildSuc(
                records,
                iPage
        );
    }

    @Override
    public List<DataDomainVO> all() {
        LambdaQueryWrapper<DataDomainPO> queryWrapper = new LambdaQueryWrapper<>();

        return baseMapper.selectList(queryWrapper).stream().map(item ->
                ConvertUtil.sourceToTarget(item, DataDomainVO.class)).collect(Collectors.toList());
    }
}
