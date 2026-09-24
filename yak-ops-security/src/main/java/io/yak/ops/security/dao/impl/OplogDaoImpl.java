package io.yak.ops.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.security.common.dto.oplog.OplogQueryDTO;
import io.yak.ops.security.common.entity.Oplog;
import io.yak.ops.security.common.po.OplogPO;
import io.yak.ops.security.dao.OplogDao;
import io.yak.ops.security.dao.mapper.OplogMapper;
import io.yak.ops.security.util.CopyBeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志数据访问实现。
 */
@Repository
@RequiredArgsConstructor
public class OplogDaoImpl implements OplogDao {

    private final OplogMapper oplogMapper;

    /** 将毫秒时间戳转换为数据库时间类型。 */
    private static Timestamp toTimestamp(Long timeMillis) {
        return timeMillis == null
                ? null
                : new Timestamp(timeMillis);
    }

    /**
     * 分页查询操作日志，不返回操作详情。
     *
     * <p>详情字段可能较长，只在详情接口中按需查询。</p>
     */
    @Override
    public IPage<Oplog> selectPageWithoutDetail(
            OplogQueryDTO queryDTO) {

        Page<OplogPO> page = Page.of(
                queryDTO.getPage(),
                queryDTO.getSize());

        LambdaQueryWrapper<OplogPO> wrapper =
                Wrappers.<OplogPO>lambdaQuery()
                        .select(
                                OplogPO::getId,
                                OplogPO::getOperateType,
                                OplogPO::getOperatePage,
                                OplogPO::getOperationMethods,
                                OplogPO::getTarget,
                                OplogPO::getTargetType,
                                OplogPO::getOperatorIp,
                                OplogPO::getOperator,
                                OplogPO::getCreateTime,
                                OplogPO::getUpdateTime)
                        .eq(
                                StringUtils.hasText(
                                        queryDTO.getOperateType()),
                                OplogPO::getOperateType,
                                trim(queryDTO.getOperateType()))
                        .eq(
                                StringUtils.hasText(
                                        queryDTO.getOperatePage()),
                                OplogPO::getOperatePage,
                                trim(queryDTO.getOperatePage()))
                        .eq(
                                StringUtils.hasText(
                                        queryDTO.getTargetType()),
                                OplogPO::getTargetType,
                                trim(queryDTO.getTargetType()))
                        .eq(
                                StringUtils.hasText(
                                        queryDTO.getOperationMethods()),
                                OplogPO::getOperationMethods,
                                trim(queryDTO.getOperationMethods()))
                        .like(
                                StringUtils.hasText(
                                        queryDTO.getDetail()),
                                OplogPO::getDetail,
                                trim(queryDTO.getDetail()))
                        .like(
                                StringUtils.hasText(
                                        queryDTO.getTarget()),
                                OplogPO::getTarget,
                                trim(queryDTO.getTarget()))
                        .like(
                                StringUtils.hasText(
                                        queryDTO.getOperator()),
                                OplogPO::getOperator,
                                trim(queryDTO.getOperator()))
                        .like(
                                StringUtils.hasText(
                                        queryDTO.getOperatorIp()),
                                OplogPO::getOperatorIp,
                                trim(queryDTO.getOperatorIp()))
                        .ge(
                                queryDTO.getStartTime() != null,
                                OplogPO::getCreateTime,
                                toTimestamp(queryDTO.getStartTime()))
                        .le(
                                queryDTO.getEndTime() != null,
                                OplogPO::getCreateTime,
                                toTimestamp(queryDTO.getEndTime()))
                        .orderByDesc(OplogPO::getCreateTime)
                        .orderByDesc(OplogPO::getId);

        IPage<OplogPO> result =
                oplogMapper.selectPage(page, wrapper);

        return CopyBeanUtil.copyPage(result, Oplog.class);
    }

    /** 根据操作日志主键查询完整日志。 */
    @Override
    public Oplog selectByOplogId(Long oplogId) {
        if (oplogId == null) {
            return null;
        }

        return CopyBeanUtil.copy(
                oplogMapper.selectById(oplogId),
                Oplog.class);
    }

    /** 新增操作日志，并回填日志主键。 */
    @Override
    public void insert(Oplog oplog) {
        OplogPO oplogPO =
                CopyBeanUtil.copy(oplog, OplogPO.class);

        if (oplogPO == null) {
            throw new IllegalStateException(
                    "操作日志持久化对象转换失败");
        }

        oplogMapper.insert(oplogPO);
        oplog.setId(oplogPO.getId());
    }

    @Override
    public List<String> listOperateType() {
        return listDistinctStrings(
                OplogPO::getOperateType);
    }

    @Override
    public List<String> listOperatePage() {
        return listDistinctStrings(
                OplogPO::getOperatePage);
    }

    @Override
    public List<String> listOperationMethods() {
        return listDistinctStrings(
                OplogPO::getOperationMethods);
    }

    @Override
    public List<String> listTargetType() {
        return listDistinctStrings(
                OplogPO::getTargetType);
    }

    /** 查询指定字符串列的非空去重值。 */
    private List<String> listDistinctStrings(
            SFunction<OplogPO, String> column) {

        List<OplogPO> records = oplogMapper.selectList(
                Wrappers.<OplogPO>lambdaQuery()
                        .select(column)
                        .isNotNull(column)
                        .groupBy(column)
                        .orderByAsc(column));

        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }

        return records.stream()
                .map(column)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
