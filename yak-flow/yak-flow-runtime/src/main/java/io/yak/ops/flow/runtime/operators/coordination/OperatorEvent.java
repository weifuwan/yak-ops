package io.yak.ops.flow.runtime.operators.coordination;

/**
 * Runtime 内部算子协调事件的统一标记。
 *
 * <p>目前使用同 JVM 内的对象交付。后续如需跨进程传输，须定义独立的序列化
 * 和发送确认协议，不应直接依赖 Java 对象的内存传递语义。
 *
 * <p>这是 Runtime 控制面消息，不承载业务数据行或 Checkpoint Barrier。
 *
 * @author weifuwan
 */
public interface OperatorEvent {}
