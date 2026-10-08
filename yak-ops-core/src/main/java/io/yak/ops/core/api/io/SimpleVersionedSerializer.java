package io.yak.ops.core.api.io;

import java.io.IOException;

/**
 * 用于 SourceSplit 与 Enumerator Checkpoint 状态的版本化二进制序列化协议。
 *
 * <p>Connector 负责对象的具体编码和向后兼容；Runtime 负责字节持久化。
 * 不要求对象使用 JDK Serializable，不得直接将活动连接或密钥写入状态。
 *
 * @param <T> 被序列化的状态或分片类型
 * @author weifuwan
 */
public interface SimpleVersionedSerializer<T> {

    /** 当前写出格式的版本号。 */
    int getVersion();

    /** 将对象转换为可保存的独立字节数组。 */
    byte[] serialize(T value) throws IOException;

    /** 根据写入时的格式版本还原对象。 */
    T deserialize(int version, byte[] serialized) throws IOException;
}
