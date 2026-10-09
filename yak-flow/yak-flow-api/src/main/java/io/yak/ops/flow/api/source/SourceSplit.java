package io.yak.ops.flow.api.source;

/**
 * Source 可独立交给 Reader 处理的一份读取范围；批量分片和 CDC 分片共享该身份协议。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface SourceSplit {

    /**
     * 返回分片在当前 Source 内稳定且唯一的标识。
     *
     * @return 分片标识
     */
    String splitId();
}
