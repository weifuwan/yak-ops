/**
 * Datasource Service Layer，负责数据源管理、连接测试以及 JDBC Plugin 能力编排。
 *
 * <p>Boot 只依赖 DataSourceService；Plugin Registry、Secret Codec 等实现细节保持在该 Capability 内部。</p>
 *
 * @author weifuwan
 * @since 2026-09-25
 */
package io.yak.ops.business.datasource;
