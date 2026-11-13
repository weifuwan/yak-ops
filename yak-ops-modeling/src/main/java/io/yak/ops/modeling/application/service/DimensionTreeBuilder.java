package io.yak.ops.modeling.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableDTO;
import io.yak.ops.modeling.api.dto.dwlayer.DwLayerDTO;
import io.yak.ops.modeling.api.vo.businessprocess.BusinessProcessVO;
import io.yak.ops.modeling.api.vo.businesstype.BusinessTypeVO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTreeNodeVO;
import io.yak.ops.modeling.api.vo.subjectdomain.SubjectDomainVO;
import io.yak.ops.modeling.domain.enums.DimensionTableTypeEnum;
import io.yak.ops.modeling.domain.enums.DimensionTreeGroupingEnum;
import io.yak.ops.modeling.infrastructure.persistence.entity.dimension.DimensionTablePO;
import io.yak.ops.modeling.infrastructure.persistence.mapper.dimension.DimensionTableMapper;
import io.yak.ops.modeling.application.service.*;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * Builds the warehouse-layer-aware dimension tree.
 *
 * <p>The original data_modal tree grouped source/public models by business type or
 * data domain, and application models by mart/subject domain. This implementation
 * keeps that payload and grouping while querying through Yak Ops project-scoped
 * services and mappers.
 */
@Component
public class DimensionTreeBuilder {

    private static final String ROOT_KEY = "DimensionTable_ROOT";
    private static final String VIRTUAL_KEY = "virtualId";

    @Resource private DimensionTableMapper dimensionTableMapper;
    @Resource private DwLayerService dwLayerService;
    @Resource private BusinessTypeService businessTypeService;
    @Resource private DataDomainService dataDomainService;
    @Resource private BusinessProcessService businessProcessService;
    @Resource private DataMartService dataMartService;
    @Resource private SubjectDomainService subjectDomainService;

    public List<DimensionTreeNodeVO> buildTree(DimensionTableDTO dto) {
        if (dto == null || dto.getDwhType() == null) {
            throw new IllegalArgumentException("数仓分层类型不能为空");
        }

        List<DimensionTablePO> tables = queryTables(dto);
        List<DimensionTreeNodeVO> nodes = new ArrayList<>();
        if (isApplicationLayer(dto)) {
            buildApplicationNodes(nodes, tables);
        } else if (isPublicLayer(dto)) {
            buildPublicNodes(nodes, tables, dto);
        } else {
            buildSourceNodes(nodes, tables, dto);
        }

        List<DimensionTreeNodeVO> roots = assemble(nodes);
        DimensionTreeNodeVO root = node(ROOT_KEY, "维度建模", null, "ROOT_NODE", false, null, null);
        root.setChildren(roots);
        for (DimensionTreeNodeVO child : roots) {
            child.setParentId(ROOT_KEY);
        }
        calculateTotals(Collections.singletonList(root));
        return Collections.singletonList(root);
    }

    private List<DimensionTablePO> queryTables(DimensionTableDTO dto) {
        QueryWrapper<DimensionTablePO> wrapper = new QueryWrapper<>();
        if (dto.getDwhType() != null) {
            List<String> layerIds = new ArrayList<>();
            dwLayerService.all(DwLayerDTO.builder().dwType(dto.getDwhType()).build())
                    .forEach(layer -> layerIds.add(layer.getId()));
            if (layerIds.isEmpty()) {
                return Collections.emptyList();
            }
            wrapper.in("dwh_id", layerIds);
        }
        if (notBlank(dto.getDwhId())) wrapper.eq("dwh_id", dto.getDwhId());
        if (notBlank(dto.getDomainId())) wrapper.eq("domain_id", dto.getDomainId());
        if (notBlank(dto.getMartId())) wrapper.eq("mart_id", dto.getMartId());
        if (notBlank(dto.getSubjectId())) wrapper.eq("subject_id", dto.getSubjectId());
        if (notBlank(dto.getDimId())) wrapper.eq("dim_id", dto.getDimId());
        if (notBlank(dto.getBusTypeId())) wrapper.eq("bus_type_id", dto.getBusTypeId());
        if (notBlank(dto.getBusProcessId())) wrapper.eq("bus_process_id", dto.getBusProcessId());
        if (notBlank(dto.getUpdateBy())) wrapper.eq("update_by", dto.getUpdateBy());
        if (notBlank(dto.getDirector())) wrapper.eq("director", dto.getDirector());
        if (dto.getSubmit() != null) wrapper.eq("submit", dto.getSubmit());
        if (notBlank(dto.getKeyword())) {
            wrapper.and(q -> q.like("cn_name", dto.getKeyword()).or().like("table_name", dto.getKeyword()));
        }
        return dimensionTableMapper.selectList(wrapper.orderByAsc("cn_name"));
    }

    private void buildSourceNodes(List<DimensionTreeNodeVO> nodes,
                                  List<DimensionTablePO> tables,
                                  DimensionTableDTO dto) {
        DimensionTreeGroupingEnum grouping = grouping(dto);
        if (grouping == DimensionTreeGroupingEnum.DATA_DOMAIN) {
            addDataDomains(nodes, dto.getDomainId());
            for (DimensionTablePO table : tables) {
                addTableNode(nodes, table, table.getDomainId());
            }
            return;
        }

        addBusinessTypes(nodes);
        boolean hasUnclassified = tables.stream().anyMatch(item -> !notBlank(item.getBusTypeId()));
        if (hasUnclassified) addNode(nodes, node(VIRTUAL_KEY, "未分类", null, "BUSINESS_TYPE", false, null, null));
        for (DimensionTablePO table : tables) {
            String parentId = notBlank(table.getBusTypeId()) ? table.getBusTypeId() : VIRTUAL_KEY;
            addTableNode(nodes, table, parentId);
        }
    }

    private void buildPublicNodes(List<DimensionTreeNodeVO> nodes,
                                  List<DimensionTablePO> tables,
                                  DimensionTableDTO dto) {
        if (grouping(dto) == DimensionTreeGroupingEnum.DATA_DOMAIN) {
            buildPublicByDomain(nodes, tables, dto);
        } else {
            buildPublicByBusinessType(nodes, tables);
        }
    }

    private void buildPublicByBusinessType(List<DimensionTreeNodeVO> nodes, List<DimensionTablePO> tables) {
        addBusinessTypes(nodes);
        Map<String, DataDomainVO> domains = new HashMap<>();
        for (DataDomainVO domain : dataDomainService.all()) domains.put(domain.getId(), domain);
        Map<String, BusinessProcessVO> processes = new HashMap<>();
        for (BusinessProcessVO process : businessProcessService.all(BusinessProcessDTO.builder().build())) {
            processes.put(process.getId(), process);
        }

        for (DimensionTablePO table : tables) {
            String businessTypeId = notBlank(table.getBusTypeId()) ? table.getBusTypeId() : VIRTUAL_KEY;
            if (VIRTUAL_KEY.equals(businessTypeId)) {
                addNode(nodes, node(VIRTUAL_KEY, "未分类", null, "BUSINESS_TYPE", false, null, null));
            }

            String domainId = notBlank(table.getDomainId()) ? table.getDomainId() : businessTypeId + "_uncategorized";
            String domainKey = businessTypeId + "_" + domainId;
            DataDomainVO domain = domains.get(table.getDomainId());
            addNode(nodes, node(domainKey, domain == null ? "未分类数据域" : domain.getCnName(),
                    businessTypeId, "DATA_DOMAIN", false, null, null));

            if (table.getTableType() == DimensionTableTypeEnum.DETAIL) {
                String folderKey = domainKey + "_detail";
                addNode(nodes, node(folderKey, "明细表", domainKey, "FOLDER", false, null, null));
                BusinessProcessVO process = processes.get(table.getBusProcessId());
                String parentId = folderKey;
                if (process != null) {
                    addNode(nodes, node(process.getId(), process.getCnName(), folderKey,
                            "BUSINESS_PROCESS", false, null, null));
                    parentId = process.getId();
                }
                addTableNode(nodes, table, parentId);
            } else {
                DimensionTableTypeEnum type = table.getTableType();
                String folderName = type == null ? "其他" : type.getName();
                String folderSuffix = type == null ? "other" : type.name().toLowerCase(Locale.ROOT);
                String folderKey = domainKey + "_" + folderSuffix;
                addNode(nodes, node(folderKey, folderName, domainKey, "FOLDER", false, null, null));
                addTableNode(nodes, table, folderKey);
            }
        }
    }

    private void buildPublicByDomain(List<DimensionTreeNodeVO> nodes,
                                     List<DimensionTablePO> tables,
                                     DimensionTableDTO dto) {
        addDataDomains(nodes, dto.getDomainId());
        Map<String, BusinessProcessVO> processes = new HashMap<>();
        for (BusinessProcessVO process : businessProcessService.all(BusinessProcessDTO.builder().build())) {
            processes.put(process.getId(), process);
        }

        for (DataDomainVO domain : dataDomainService.all()) {
            if (notBlank(dto.getDomainId()) && !dto.getDomainId().equals(domain.getId())) continue;
            addNode(nodes, node(domain.getId() + "_dim", "维度", domain.getId(), "FOLDER", false, null, null));
            addNode(nodes, node(domain.getId() + "_dimension", "维度表", domain.getId(), "FOLDER", false, null, null));
            String detailFolder = domain.getId() + "_detail";
            addNode(nodes, node(detailFolder, "明细表", domain.getId(), "FOLDER", false, null, null));
            addNode(nodes, node(domain.getId() + "_summary", "汇总表", domain.getId(), "FOLDER", false, null, null));
            for (BusinessProcessVO process : processes.values()) {
                if (domain.getId().equals(process.getDataDomainId())) {
                    addNode(nodes, node(process.getId(), process.getCnName(), detailFolder,
                            "BUSINESS_PROCESS", false, null, null));
                }
            }
        }

        for (DimensionTablePO table : tables) {
            String domainId = table.getDomainId();
            String parentId = domainId;
            if (table.getTableType() == DimensionTableTypeEnum.DETAIL) {
                parentId = processes.containsKey(table.getBusProcessId())
                        ? table.getBusProcessId() : domainId + "_detail";
            } else if (table.getTableType() == DimensionTableTypeEnum.DIM) {
                parentId = domainId + "_dim";
            } else if (table.getTableType() == DimensionTableTypeEnum.DIMENSION) {
                parentId = domainId + "_dimension";
            } else if (table.getTableType() == DimensionTableTypeEnum.SUMMARY) {
                parentId = domainId + "_summary";
            }
            addTableNode(nodes, table, parentId);
        }
    }

    private void buildApplicationNodes(List<DimensionTreeNodeVO> nodes, List<DimensionTablePO> tables) {
        addBusinessTypes(nodes);
        Map<String, DataMartVO> marts = new LinkedHashMap<>();
        for (DataMartVO mart : dataMartService.all()) marts.put(mart.getId(), mart);
        Map<String, SubjectDomainVO> subjects = new LinkedHashMap<>();
        for (SubjectDomainVO subject : subjectDomainService.all()) subjects.put(subject.getId(), subject);

        boolean hasUnclassified = tables.stream()
                .anyMatch(item -> !notBlank(item.getMartId()) && !notBlank(item.getSubjectId()))
                || marts.values().stream().anyMatch(item -> !notBlank(item.getBusinessTypeId()) && !notBlank(item.getParentId()));
        if (hasUnclassified) addNode(nodes, node(VIRTUAL_KEY, "未分类", null, "BUSINESS_TYPE", false, null, null));

        for (DataMartVO mart : marts.values()) {
            String parentId = notBlank(mart.getParentId()) ? mart.getParentId()
                    : (notBlank(mart.getBusinessTypeId()) ? mart.getBusinessTypeId() : VIRTUAL_KEY);
            addNode(nodes, node(mart.getId(), mart.getMartName(), parentId, "MART", false, null, null));
        }
        for (SubjectDomainVO subject : subjects.values()) {
            String parentId = notBlank(subject.getParentId()) ? subject.getParentId() : subject.getDataMartId();
            addNode(nodes, node(subject.getId(), subject.getDomainName(), parentId, "SUBJECT", false, null, null));
        }
        for (DimensionTablePO table : tables) {
            String parentId = notBlank(table.getSubjectId()) ? table.getSubjectId()
                    : (notBlank(table.getMartId()) ? table.getMartId() : VIRTUAL_KEY);
            addTableNode(nodes, table, parentId);
        }
    }

    private void addBusinessTypes(List<DimensionTreeNodeVO> nodes) {
        for (BusinessTypeVO businessType : businessTypeService.all()) {
            addNode(nodes, node(businessType.getId(), businessType.getBusinessName(),
                    businessType.getParentId(), "BUSINESS_TYPE", false, null, null));
        }
    }

    private void addDataDomains(List<DimensionTreeNodeVO> nodes, String selectedDomainId) {
        for (DataDomainVO domain : dataDomainService.all()) {
            if (notBlank(selectedDomainId) && !selectedDomainId.equals(domain.getId())) continue;
            addNode(nodes, node(domain.getId(), domain.getCnName(), null, "DATA_DOMAIN", false, null, null));
        }
    }

    private void addTableNode(List<DimensionTreeNodeVO> nodes, DimensionTablePO table, String parentId) {
        String type = table.getTableType() == null ? "DIM" : table.getTableType().name();
        addNode(nodes, node(table.getId() + "-DIM", table.getCnName(), parentId,
                type, true, table.getUpdateTime(), null));
    }

    private List<DimensionTreeNodeVO> assemble(List<DimensionTreeNodeVO> nodes) {
        Map<String, DimensionTreeNodeVO> byKey = new LinkedHashMap<>();
        for (DimensionTreeNodeVO item : nodes) byKey.put(item.getKey(), item);
        List<DimensionTreeNodeVO> roots = new ArrayList<>();
        for (DimensionTreeNodeVO item : byKey.values()) {
            DimensionTreeNodeVO parent = item.getParentId() == null ? null : byKey.get(item.getParentId());
            if (parent == null) roots.add(item);
            else parent.getChildren().add(item);
        }
        return roots;
    }

    private void calculateTotals(List<DimensionTreeNodeVO> nodes) {
        for (DimensionTreeNodeVO item : nodes) {
            calculateTotals(item.getChildren());
            int total = item.isLeaf() ? 1 : 0;
            for (DimensionTreeNodeVO child : item.getChildren()) {
                total += child.getTotal() == null ? 0 : child.getTotal();
            }
            item.setTotal(total);
        }
    }

    private DimensionTreeNodeVO node(String key, String title, String parentId, String type,
                                     boolean leaf, Date updateTime, Integer total) {
        DimensionTreeNodeVO node = new DimensionTreeNodeVO();
        node.setKey(key);
        node.setTitle(title);
        node.setParentId(parentId);
        node.setType(type);
        node.setLeaf(leaf);
        node.setUpdateTime(updateTime);
        node.setTotal(total);
        return node;
    }

    private void addNode(List<DimensionTreeNodeVO> nodes, DimensionTreeNodeVO candidate) {
        for (DimensionTreeNodeVO current : nodes) {
            if (current.getKey().equals(candidate.getKey())) return;
        }
        nodes.add(candidate);
    }

    private DimensionTreeGroupingEnum grouping(DimensionTableDTO dto) {
        return dto.getSelectOption() == null
                ? DimensionTreeGroupingEnum.BUSINESS_TYPE : dto.getSelectOption();
    }

    private boolean isApplicationLayer(DimensionTableDTO dto) {
        return dto.getDwhType() == io.yak.ops.modeling.domain.enums.DwTypeEnum.APPLICATION_LAYER;
    }

    private boolean isPublicLayer(DimensionTableDTO dto) {
        return dto.getDwhType() == io.yak.ops.modeling.domain.enums.DwTypeEnum.PUBLIC_LAYER;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
