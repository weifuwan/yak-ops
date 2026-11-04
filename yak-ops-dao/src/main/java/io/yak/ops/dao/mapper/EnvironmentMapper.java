package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.Environment;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface EnvironmentMapper extends BaseMapper<Environment> {

    /**
     * query environment by name
     *
     * @param name name
     * @return environment
     */
    Environment queryByEnvironmentName(@Param("environmentName") String name);

    /**
     * query environment by code
     *
     * @param environmentCode environmentCode
     * @return environment
     */
    Environment queryByEnvironmentCode(@Param("environmentCode") Long environmentCode);

    /**
     * query all environment list
     * @return environment list
     */
    List<Environment> queryAllEnvironmentList();

    /**
     * environment page
     * @param page page
     * @param searchName searchName
     * @return environment IPage
     */
    IPage<Environment> queryEnvironmentListPaging(IPage<Environment> page, @Param("searchName") String searchName);

    /**
     * delete environment by code
     *
     * @param code code
     * @return int
     */
    int deleteByCode(@Param("code") Long code);

    /**
     * queryEnvironmentListPagingByIds
     * @param page
     * @param ids
     * @param searchVal
     * @return
     */
    IPage<Environment> queryEnvironmentListPagingByIds(Page<Environment> page, @Param("ids") List<Integer> ids,
                                                       @Param("searchName") String searchVal);
}
