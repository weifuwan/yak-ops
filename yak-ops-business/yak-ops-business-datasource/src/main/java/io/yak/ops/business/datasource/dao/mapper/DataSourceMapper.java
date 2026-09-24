package io.yak.ops.business.datasource.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import org.apache.ibatis.annotations.Mapper;

/** 数据源 MyBatis 映射接口。 */
@Mapper
public interface DataSourceMapper extends BaseMapper<DataSourcePO> {
  DataSourceSummaryRow selectSummary();
}
