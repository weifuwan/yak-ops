package io.yak.ops.api.vo;

import io.yak.ops.dao.entity.DataSource;

import lombok.Data;

@Data
public class DataSourceSimpleInfoVO {

    private Integer id;

    private String name;

    public DataSourceSimpleInfoVO(DataSource dataSource) {
        this.id = dataSource.getId();
        this.name = dataSource.getName();
    }
}
