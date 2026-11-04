package io.yak.ops.eventbus;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import lombok.Builder;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * The abstract class of delay event, the event will be triggered after the delay time.
 * <p> You can extend this class to implement your own delay event.
 */
@ToString
@SuperBuilder
public abstract class AbstractDelayEvent implements IEvent, Delayed {

    private static final long DEFAULT_DELAY_TIME = 0;

    // In milliseconds
    protected long delayTime;

    @Builder.Default
    protected long createTimeInNano = System.nanoTime();

    // set create time as default if the inheritor didn't call super()
    @Builder.Default
    protected long expiredTimeInNano = System.nanoTime();

    public AbstractDelayEvent() {
        this(DEFAULT_DELAY_TIME);
    }

    public AbstractDelayEvent(final long delayTime) {
        this(delayTime, System.nanoTime());
    }

    public AbstractDelayEvent(final long delayTime, final long createTimeInNano) {
        this.delayTime = delayTime;
        this.createTimeInNano = createTimeInNano;
        this.expiredTimeInNano = this.delayTime * 1_000_000 + this.createTimeInNano;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long delay = createTimeInNano + delayTime * 1_000_000 - System.nanoTime();
        return unit.convert(delay, TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        return Long.compare(this.expiredTimeInNano, ((AbstractDelayEvent) other).expiredTimeInNano);
    }

}
