package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.dao.entity.DerivativeIndicatorPO;
import io.yak.ops.dao.entity.TimePeriodCategoryPO;
import io.yak.ops.dao.entity.TimePeriodPO;
import io.yak.ops.modeling.api.dto.timeperiod.TimePeriodCategoryDTO;
import io.yak.ops.modeling.api.dto.timeperiod.TimePeriodDTO;
import io.yak.ops.modeling.api.vo.timeperiod.TimePeriodCategoryVO;
import io.yak.ops.modeling.api.vo.timeperiod.TimePeriodVO;
import io.yak.ops.modeling.application.service.TimePeriodService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Time-period page service, including its category management. */
@Service
public class TimePeriodServiceImpl
        extends AbstractVersionedModelingAssetService<TimePeriodPO, TimePeriodDTO, TimePeriodVO>
        implements TimePeriodService {

    @Override
    protected String resourceKey() {
        return "time-period";
    }

    @Override
    protected String versionType() {
        return "TIME_PERIOD";
    }

    @Override
    protected Class<TimePeriodPO> entityClass() {
        return TimePeriodPO.class;
    }

    @Override
    protected Class<TimePeriodDTO> dtoClass() {
        return TimePeriodDTO.class;
    }

    @Override
    protected Class<TimePeriodVO> viewClass() {
        return TimePeriodVO.class;
    }

    @Override
    protected QueryWrapper<TimePeriodPO> buildQueryWrapper(TimePeriodDTO query) {
        QueryWrapper<TimePeriodPO> wrapper = new QueryWrapper<TimePeriodPO>();
        addKeyword(wrapper, query.getKeyword(), "cn_name", "en_short_name", "en_name");
        eqIfNotBlank(wrapper, query.getCategoryId(), "category_id");
        eqIfPresent(wrapper, query.getSubmit(), "submit");
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IPage<TimePeriodVO> page(TimePeriodDTO query) {
        ensureDefaultTimePeriods();
        return super.page(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TimePeriodVO> list(TimePeriodDTO query) {
        ensureDefaultTimePeriods();
        return super.list(query);
    }

    @Override
    protected void validate(TimePeriodDTO dto) {
        requireFields(dto, "cnName", "categoryId", "enShortName", "director");
        validateUniqueFields(resourceKey(), dto, dto.getId());
        requireExists("time-period-category", dto.getCategoryId(), "时间周期目录不存在");
    }

    @Override
    protected void beforeCreate(TimePeriodDTO dto) {
        dto.setSubmit(Boolean.FALSE);
    }

    @Override
    protected void beforeUpdate(TimePeriodDTO dto, TimePeriodPO existing) {
        dto.setSubmit(Boolean.FALSE);
    }

    @Override
    protected void beforeDelete(TimePeriodPO existing) {
        if (modelingAssetDao.queryCount("derivative-indicator",
                new QueryWrapper<DerivativeIndicatorPO>().eq("time_period_id", existing.getId())) > 0) {
            throw new IllegalArgumentException("该时间周期已被派生指标引用，不能删除");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IPage<TimePeriodCategoryVO> pageCategories(TimePeriodCategoryDTO query) {
        ensureDefaultTimePeriodCategories();
        TimePeriodCategoryDTO actual = query == null ? new TimePeriodCategoryDTO() : query;
        int pageNo = positive(actual.getPageNo(), 1);
        int pageSize = Math.min(positive(actual.getPageSize(), 10), 100);
        IPage<TimePeriodCategoryPO> source = modelingAssetDao.queryPage(
                "time-period-category",
                new Page<TimePeriodCategoryPO>(pageNo, pageSize),
                buildCategoryWrapper(actual).orderByDesc("update_time"));
        Page<TimePeriodCategoryVO> result = new Page<TimePeriodCategoryVO>(
                source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(toCategoryViews(source.getRecords()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TimePeriodCategoryVO> listCategories(TimePeriodCategoryDTO query) {
        ensureDefaultTimePeriodCategories();
        TimePeriodCategoryDTO actual = query == null ? new TimePeriodCategoryDTO() : query;
        return toCategoryViews(modelingAssetDao.queryList(
                "time-period-category", buildCategoryWrapper(actual).orderByDesc("update_time")));
    }

    @Override
    public TimePeriodCategoryVO getCategoryById(String id) {
        return toCategoryView(requireCategory(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createCategory(TimePeriodCategoryDTO dto, String operator) {
        validateCategory(dto);
        if (notBlank(dto.getParentId())) {
            requireCategory(dto.getParentId());
        }
        TimePeriodCategoryPO entity = new TimePeriodCategoryPO();
        BeanUtils.copyProperties(dto, entity);
        entity.setId(null);
        entity.setProjectCode(null);
        entity.initInsert();
        entity.setCreateBy(resolveOperator(operator));
        entity.setUpdateBy(resolveOperator(operator));
        modelingAssetDao.insert("time-period-category", entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TimePeriodCategoryVO updateCategory(String id, TimePeriodCategoryDTO dto, String operator) {
        validateCategory(dto);
        TimePeriodCategoryPO existing = requireCategory(id);
        if (id.equals(dto.getParentId())) {
            throw new IllegalArgumentException("时间周期目录不能将自身设为父目录");
        }
        if (notBlank(dto.getParentId())) {
            requireCategory(dto.getParentId());
        }
        TimePeriodCategoryPO entity = new TimePeriodCategoryPO();
        BeanUtils.copyProperties(dto, entity);
        entity.setId(id);
        entity.setProjectCode(null);
        entity.initUpdate();
        entity.setUpdateBy(resolveOperator(operator));
        modelingAssetDao.updateById("time-period-category", entity);
        return getCategoryById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteCategory(String id) {
        TimePeriodCategoryPO existing = requireCategory(id);
        if (modelingAssetDao.queryCount("time-period-category",
                new QueryWrapper<TimePeriodCategoryPO>().eq("parent_id", id)) > 0
                || modelingAssetDao.queryCount("time-period",
                new QueryWrapper<TimePeriodPO>().eq("category_id", id)) > 0) {
            throw new IllegalArgumentException("该周期目录下仍有子目录或时间周期，不能删除");
        }
        modelingAssetDao.deleteById("time-period-category", existing.getId());
        return id;
    }

    private QueryWrapper<TimePeriodCategoryPO> buildCategoryWrapper(TimePeriodCategoryDTO query) {
        QueryWrapper<TimePeriodCategoryPO> wrapper = new QueryWrapper<TimePeriodCategoryPO>();
        addCategoryKeyword(wrapper, query.getKeyword());
        if (notBlank(query.getParentId())) {
            wrapper.eq("parent_id", query.getParentId());
        }
        if (query.getLeaf() != null) {
            wrapper.eq("leaf", query.getLeaf());
        }
        return wrapper;
    }

    private void addCategoryKeyword(QueryWrapper<TimePeriodCategoryPO> wrapper, String keyword) {
        if (notBlank(keyword)) {
            wrapper.and(condition -> condition.like("cn_name", keyword).or().like("en_name", keyword));
        }
    }

    private void validateCategory(TimePeriodCategoryDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        requireFields(dto, "cnName");
    }

    private TimePeriodCategoryPO requireCategory(String id) {
        if (!notBlank(id)) {
            throw new IllegalArgumentException("时间周期目录ID不能为空");
        }
        TimePeriodCategoryPO entity = modelingAssetDao.queryById("time-period-category", id);
        if (entity == null) {
            throw new IllegalArgumentException("时间周期目录不存在或不属于当前项目");
        }
        return entity;
    }

    private TimePeriodCategoryVO toCategoryView(TimePeriodCategoryPO entity) {
        TimePeriodCategoryVO view = new TimePeriodCategoryVO();
        BeanUtils.copyProperties(entity, view);
        return view;
    }

    private List<TimePeriodCategoryVO> toCategoryViews(List<TimePeriodCategoryPO> entities) {
        List<TimePeriodCategoryVO> result = new ArrayList<TimePeriodCategoryVO>(entities.size());
        for (TimePeriodCategoryPO entity : entities) {
            result.add(toCategoryView(entity));
        }
        return result;
    }

    private void ensureDefaultTimePeriodCategories() {
        if (modelingAssetDao.queryCount("time-period-category",
                new QueryWrapper<TimePeriodCategoryPO>()) > 0) {
            return;
        }
        TimePeriodCategoryPO root = new TimePeriodCategoryPO();
        root.setCnName("时间周期");
        root.setEnName("TIME_PERIOD");
        root.setLeaf(Boolean.FALSE);
        root.setProjectCode(null);
        root.initInsert();
        modelingAssetDao.insert("time-period-category", root);

        String[][] categories = {
                {"天", "DAY"}, {"周", "WEEK"}, {"月", "MONTH"}, {"季度", "QUARTER"},
                {"年", "YEAR"}, {"小时", "HOUR"}, {"分钟", "MINUTE"}, {"秒", "SECOND"},
                {"历史全量", "HISTORY"}
        };
        for (String[] category : categories) {
            TimePeriodCategoryPO item = new TimePeriodCategoryPO();
            item.setCnName(category[0]);
            item.setEnName(category[1]);
            item.setParentId(root.getId());
            item.setLeaf(Boolean.TRUE);
            item.setProjectCode(null);
            item.initInsert();
            modelingAssetDao.insert("time-period-category", item);
        }
    }

    private void ensureDefaultTimePeriods() {
        ensureDefaultTimePeriodCategories();
        if (modelingAssetDao.queryCount("time-period", new QueryWrapper<TimePeriodPO>()) > 0) {
            return;
        }
        Map<String, String> categoryIds = new HashMap<String, String>();
        for (TimePeriodCategoryPO category : modelingAssetDao.queryList(
                "time-period-category", new QueryWrapper<TimePeriodCategoryPO>())) {
            if (category.getEnName() != null) {
                categoryIds.put(category.getEnName(), category.getId());
            }
        }
        String[][] periods = {
                {"DAY", "1d", "last 1 day", "近1天", "最近1天"},
                {"DAY", "3d", "last 3 day", "近3天", "最近3天"},
                {"WEEK", "1w", "last 7 day", "近7天", "最近7天"},
                {"MONTH", "1m", "last 30 day", "近30天", "最近30天"},
                {"WEEK", "cw", "calendar week", "自然周", "自然周（周一至周日）"},
                {"MONTH", "cm", "calendar month", "自然月", "自然月（每月1日至月末）"},
                {"QUARTER", "cq", "calendar quarter", "自然季度", "自然季度"}
        };
        for (String[] data : periods) {
            String categoryId = categoryIds.get(data[0]);
            if (categoryId == null) {
                continue;
            }
            TimePeriodPO item = new TimePeriodPO();
            item.setCategoryId(categoryId);
            item.setEnShortName(data[1]);
            item.setEnName(data[2]);
            item.setCnName(data[3]);
            item.setRemark(data[4]);
            item.setSubmit(Boolean.FALSE);
            item.setProjectCode(null);
            item.initInsert();
            modelingAssetDao.insert("time-period", item);
        }
    }
}
