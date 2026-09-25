package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.IdUtils;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据库实体基础类，统一主键与审计字段。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Getter
@Setter
public abstract class BaseEntity {

    /** 主键 ID，由统一雪花算法生成。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人标识。 */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** 更新人标识。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    public void initCreate() {
        initCreate(CommonConstants.SYSTEM_USER);
    }

    public void initCreate(String userId) {
        LocalDateTime now = DateUtils.now();
        if (id == null || id.isBlank()) {
            id = IdUtils.nextId();
        }
        createTime = now;
        updateTime = now;
        createBy = normalizeUser(userId);
        updateBy = createBy;
    }

    public void initUpdate() {
        initUpdate(CommonConstants.SYSTEM_USER);
    }

    public void initUpdate(String userId) {
        updateTime = DateUtils.now();
        updateBy = normalizeUser(userId);
    }

    private static String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? CommonConstants.SYSTEM_USER : userId.strip();
    }
}
