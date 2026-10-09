package io.yak.ops.flow.runtime.operators;

import io.yak.ops.core.api.operators.Collector;

/**
 * 处理单路输入的 StreamOperator。每个并行子任务独立创建实例，生命周期和记录处理
 * 都由所属 StreamTask 的 Mailbox 线程串行调用。
 *
 * <p>输出只能通过 Collector 同步传递，不得保存后在其它线程中使用。
 * 输入正常结束时调用 finish(Collector)，失败或取消不调用。
 */
public interface OneInputStreamOperator<IN, OUT> extends StreamOperator {

    /** 消费一条记录，同步输出零条或多条记录。 */
    void processElement(IN element, Collector<OUT> output) throws Exception;

    /** 正常结束时输出剩余缓冲记录；默认调用 StreamOperator.finish()。 */
    default void finish(Collector<OUT> output) throws Exception {
        finish();
    }
}
