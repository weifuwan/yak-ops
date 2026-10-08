package io.yak.ops.flow.runtime.source.event;

import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;

/** Enumerator 已向指定 Reader 发出全部分片，不代表 Reader 已经读完。 */
public record NoMoreSplitsEvent() implements OperatorEvent {}
