package io.yak.ops.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.security.common.po.MessagePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息 MyBatis 映射接口。
 */
@Mapper
public interface MessageMapper extends BaseMapper<MessagePO> {
}
