package io.yak.ops.plugin.datasource.api.catalog;

/**
 * 数据源字段元数据。
 *
 * @param name 字段名称
 * @param typeName 数据源原生类型名称
 * @param jdbcType JDBC Types 对应的类型编码
 * @param size 字段长度或精度
 * @param scale 小数位数
 * @param nullable 是否允许为空
 * @param ordinalPosition 字段顺序
 * @param primaryKey 是否为主键字段
 * @param remarks 数据源字段备注
 * @author weifuwan
 * @since 2026-09-24
 */
public record DataSourceColumn(
        String name,
        String typeName,
        int jdbcType,
        Integer size,
        Integer scale,
        boolean nullable,
        int ordinalPosition,
        boolean primaryKey,
        String remarks) {}
