package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.ProjectParameter;

import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface ProjectParameterMapper extends BaseMapper<ProjectParameter> {

    ProjectParameter queryByCode(@Param("code") long code);

    List<ProjectParameter> queryByCodes(@Param("codes") Collection<Long> codes);

    ProjectParameter queryByName(@Param("paramName") String paramName);

    IPage<ProjectParameter> queryProjectParameterListPaging(IPage<ProjectParameter> page,
                                                            @Param("projectCode") long projectCode,
                                                            @Param("projectParameterIds") List<Integer> projectParameterIds,
                                                            @Param("searchName") String searchName,
                                                            @Param("projectParameterDataType") String projectParameterDataType);

    List<ProjectParameter> queryByProjectCode(@Param("projectCode") long projectCode);
}
