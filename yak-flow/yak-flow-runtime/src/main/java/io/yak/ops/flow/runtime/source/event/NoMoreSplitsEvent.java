package io.yak.ops.flow.runtime.source.event;

import io.yak.ops.flow.runtime.operators.coordination.OperatorEvent;

/** Signals no further splits will arrive; a Reader may still have unfinished work. */
public record NoMoreSplitsEvent() implements OperatorEvent {}
