package io.yak.ops.plugin.datasource.api.catalog;

/**
 * 数据源表、视图、Collection 或 Index 元数据。
 *
 * @param database 所属数据库名称
 * @param schema 所属 Schema 名称；无 Schema 概念的数据源为空
 * @param name 元数据对象名称
 * @param type 元数据类型，例如 TABLE、VIEW、COLLECTION、INDEX
 * @param remarks 数据源对象备注
 * @author weifuwan
 * @since 2026-09-24
 */
public record DataSourceTable(String database, String schema, String name, String type, String remarks) {}
