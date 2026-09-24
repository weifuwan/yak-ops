package io.yak.ops.security.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.security.common.entity.OplogExtra;
import io.yak.ops.security.common.po.OplogExtraPO;
import io.yak.ops.security.dao.OplogExtraDao;
import io.yak.ops.security.dao.mapper.OplogExtraMapper;
import io.yak.ops.security.util.CopyBeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 操作日志扩展信息数据访问实现。
 */
@Repository
@RequiredArgsConstructor
public class OplogExtraDaoImpl
        implements OplogExtraDao {

    private final OplogExtraMapper oplogExtraMapper;

    /**
     * 根据扩展信息类型查询日志扩展信息。
     *
     * @param type 扩展信息类型
     * @return 操作日志扩展信息列表
     */
    @Override
    public List<OplogExtra> selectListByType(Integer type) {
        if (type == null) {
            return java.util.Collections.emptyList();
        }

        List<OplogExtraPO> oplogExtraPOList =
                oplogExtraMapper.selectList(
                        Wrappers.<OplogExtraPO>lambdaQuery()
                                .eq(OplogExtraPO::getType, type)
                                .orderByAsc(OplogExtraPO::getId)
                );

        return CopyBeanUtil.copyList(
                oplogExtraPOList,
                OplogExtra.class
        );
    }

    /**
     * 批量新增操作日志扩展信息。
     *
     * <p>当前采用循环插入方式，适合单次数据量较小的场景。</p>
     *
     * @param oplogExtraList 操作日志扩展信息列表
     */
    @Override
    public void insertBatch(
            List<OplogExtra> oplogExtraList) {

        if (oplogExtraList == null
                || oplogExtraList.isEmpty()) {
            return;
        }

        CopyBeanUtil.copyList(
                oplogExtraList,
                OplogExtraPO.class
        )
                .forEach(oplogExtraMapper::insert);
    }
}