package io.yak.ops.modeling.infrastructure.persistence.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.modeling.infrastructure.persistence.entity.subjectdomain.SubjectDomainPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 主题域
 *
 * @author bruce
 */
@Mapper
public interface SubjectDomainMapper extends BaseMapper<SubjectDomainPO> {

}
