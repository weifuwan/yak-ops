package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.modeling.api.dto.subjectdomain.SubjectDomainDTO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.dao.entity.SubjectDomainPO;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.api.vo.subjectdomain.SubjectDomainVO;
import io.yak.ops.modeling.api.vo.tree.TreeTypeValueVO;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.support.TreeUtil;
import io.yak.ops.modeling.application.service.DataMartService;
import io.yak.ops.modeling.application.service.SubjectDomainService;
import io.yak.ops.dao.repository.modeling.SubjectDomainDao;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubjectDomainServiceImpl implements SubjectDomainService {

    private static final String MART = "MART";
    private static final String SUBJECT = "SUBJECT";

    @Resource
    private DataMartService dataMartService;

    @Resource
    private SubjectDomainDao subjectDomainDao;

    @Override
    public String create(SubjectDomainDTO SubjectDomain) {
        SubjectDomainPO SubjectDomainPO = ConvertUtil.sourceToTarget(SubjectDomain, SubjectDomainPO.class);
        SubjectDomainPO.initInsert();
        subjectDomainDao.insert(SubjectDomainPO);
        return SubjectDomainPO.getId();
    }

    @Override
    public SubjectDomainVO updateById(SubjectDomainDTO SubjectDomain) {
        SubjectDomainPO SubjectDomainPO = ConvertUtil.sourceToTarget(SubjectDomain, SubjectDomainPO.class);
        SubjectDomainPO.initUpdate();
        subjectDomainDao.updateById(SubjectDomainPO);
        return ConvertUtil.sourceToTarget(SubjectDomainPO, SubjectDomainVO.class);
    }

    @Override
    public String delete(String id) {
        subjectDomainDao.deleteById(id);
        return id;
    }

    @Override
    public SubjectDomainVO selectById(String id) {
        SubjectDomainPO SubjectDomainPO = subjectDomainDao.queryById(id);
        SubjectDomainVO vo = ConvertUtil.sourceToTarget(SubjectDomainPO, SubjectDomainVO.class);
        DataMartVO dataMartVO = dataMartService.selectById(vo.getDataMartId());
        vo.setDataMartName(dataMartVO.getMartName());
        return vo;
    }

    @Override
    public PaginationResult<SubjectDomainVO> paging(SubjectDomainDTO dto) {
        IPage<SubjectDomainPO> iPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<SubjectDomainPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getDataMartId() != null, SubjectDomainPO::getDataMartId, dto.getDataMartId())
                .eq(dto.getId() != null, SubjectDomainPO::getParentId, dto.getId())
                .orderByDesc(SubjectDomainPO::getUpdateTime);
        IPage<SubjectDomainPO> result = subjectDomainDao.queryPage(iPage, wrapper);
        List<SubjectDomainVO> records = ConvertUtil.sourceListToTarget(result.getRecords(), SubjectDomainVO.class);
        return PaginationResult.buildSuc(
                records,
                iPage
        );
    }

    @Override
    public List<TreeTypeValueVO> tree() {
        List<TreeTypeValueVO> treeList = new ArrayList<>();

        // 添加数据集市节点
        dataMartService.all().forEach(item -> {
            String id = item.getId() + "-" + MART;
            String parentId = item.getParentId() != null ? item.getParentId() + "-" + MART : null;
            treeList.add(new TreeTypeValueVO(id, item.getMartName(), parentId, MART, null,id));
        });

        // 添加主题域
        all().forEach(item -> {
            String id = item.getId() + "-" + SUBJECT;
            String parentId = determineParentId(item);
            treeList.add(new TreeTypeValueVO(id, item.getDomainName(), parentId, SUBJECT, null,id));
        });

        return new TreeUtil<TreeTypeValueVO>().getTreeByList(treeList);
    }

    /**
     * 确定父节点ID
     */
    private String determineParentId(SubjectDomainVO item) {
        if (StringUtils.isNotBlank(item.getParentId())) {
            return item.getParentId() + "-" + SUBJECT;
        }
        return item.getDataMartId() + "-" + MART;
    }

    @Override
    public List<SubjectDomainVO> all() {
        LambdaQueryWrapper<SubjectDomainPO> queryWrapper = new LambdaQueryWrapper<>();

        return subjectDomainDao.queryList(queryWrapper).stream().map(item -> ConvertUtil.sourceToTarget(item, SubjectDomainVO.class)).collect(Collectors.toList());
    }

}
