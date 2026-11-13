package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.modeling.api.dto.dwlayer.DwLayerDTO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.dao.entity.DwLayerPO;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.dao.entity.enums.DwModelEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.application.service.DwLayerService;
import io.yak.ops.dao.repository.modeling.DwLayerDao;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DwLayerServiceImpl implements DwLayerService {

    @javax.annotation.Resource
    private DwLayerDao dwLayerDao;

    @Override
    public String create(DwLayerDTO dto) {
        DwLayerPO po = ConvertUtil.sourceToTarget(dto, DwLayerPO.class);
        po.initInsert();
        dwLayerDao.insert(po);
        return po.getId();
    }

    @Override
    public DwLayerVO updateById(DwLayerDTO dto) {
        DwLayerPO po = ConvertUtil.sourceToTarget(dto, DwLayerPO.class);
        po.initUpdate();
        if (dto.getId() == null) {
            throw new RuntimeException("主键ID不能为空");
        }
        dwLayerDao.updateById(po);
        return ConvertUtil.sourceToTarget(po, DwLayerVO.class);
    }

    @Override
    public String delete(String id) {
        dwLayerDao.deleteById(id);
        return id;
    }

    @Override
    public DwLayerVO selectById(String id) {
        DwLayerPO DwLayerPO = dwLayerDao.queryById(id);
        return ConvertUtil.sourceToTarget(DwLayerPO, DwLayerVO.class);
    }

    @Override
    public DwLayerVO selectByDwType(String dwType) {
        LambdaQueryWrapper<DwLayerPO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(dwType)) {
            wrapper.eq(DwLayerPO::getDwType, dwType);
            if (DwTypeEnum.PUBLIC_LAYER.getCode().equals(dwType)) {
                wrapper.eq(DwLayerPO::getDwModelType, DwModelEnum.DETAIL);
            }
        }
        DwLayerPO dwLayerPO = dwLayerDao.queryOne(wrapper);
        return ConvertUtil.sourceToTarget(dwLayerPO, DwLayerVO.class);
    }

    @Override
    public PaginationResult<DwLayerVO> paging(DwLayerDTO dto) {
        IPage<DwLayerPO> iPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<DwLayerPO> wrapper = new LambdaQueryWrapper<>();

        wrapper.orderByDesc(DwLayerPO::getUpdateTime);
        IPage<DwLayerPO> result = dwLayerDao.queryPage(iPage, wrapper);
        List<DwLayerVO> records = ConvertUtil.sourceListToTarget(result.getRecords(), DwLayerVO.class);
        return PaginationResult.buildSuc(
                records,
                iPage
        );
    }

    @Override
    public List<DwLayerVO> all(DwLayerDTO dto) {
        LambdaQueryWrapper<DwLayerPO> queryWrapper = new LambdaQueryWrapper<>();
        if (dto.getDwType() != null) {
            queryWrapper.eq(DwLayerPO::getDwType, dto.getDwType());
        }
        return dwLayerDao.queryList(queryWrapper).stream().map(item -> ConvertUtil.sourceToTarget(item, DwLayerVO.class)).collect(Collectors.toList());
    }

    @Override
    public List<OptionVO> option(List<String> dwType) {
        LambdaQueryWrapper<DwLayerPO> queryWrapper = new LambdaQueryWrapper<>();
        if (CollectionUtils.isNotEmpty(dwType)) {
            queryWrapper.in(DwLayerPO::getDwModelType, dwType);
        }
        return convert(queryWrapper);
    }

    @Override
    public List<OptionVO> optionByModalType(DwModelEnum modalType) {
        LambdaQueryWrapper<DwLayerPO> queryWrapper = new LambdaQueryWrapper<>();
        if (modalType != null) {
            queryWrapper.in(DwLayerPO::getDwModelType, modalType);
        }
        return convert(queryWrapper);
    }


    private List<OptionVO> convert(LambdaQueryWrapper<DwLayerPO> queryWrapper) {
        return dwLayerDao.queryList(queryWrapper).stream().map(item -> {
            OptionVO optionVO = new OptionVO();
            DwLayerVO vo = ConvertUtil.sourceToTarget(item, DwLayerVO.class);
            optionVO.setLabel(vo);
            optionVO.setValue(item.getId());
            return optionVO;
        }).collect(Collectors.toList());
    }
}
