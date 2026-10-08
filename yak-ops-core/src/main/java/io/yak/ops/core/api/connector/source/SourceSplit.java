package io.yak.ops.core.api.connector.source;

/**
 * Source 中一份可独立分配给 Reader 的读取任务。
 *
 * <p>Split 的具体范围、分区、游标等由 Connector 定义；Core 不解析数据库或消息系统细节。
 * splitId 在同一 Source 内必须稳定且唯一，重试和恢复不能随意改变。
 *
 * <p>用于检查点恢复的 Split 状态还应携带必要读取进度，不能只有分片标识。
 *
 * @author weifuwan
 */
public interface SourceSplit {

    /** 返回当前 Source 内稳定且唯一的非空分片 ID。 */
    String splitId();
}
