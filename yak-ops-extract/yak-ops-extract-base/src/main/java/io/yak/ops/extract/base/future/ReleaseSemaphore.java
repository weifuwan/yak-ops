package io.yak.ops.extract.base.future;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReleaseSemaphore {

    private final Semaphore semaphore;

    private final AtomicBoolean released;

    public ReleaseSemaphore(Semaphore semaphore) {
        this.semaphore = semaphore;
        this.released = new AtomicBoolean(false);
    }

    public void release() {
        if (this.released.compareAndSet(false, true)) {
            this.semaphore.release();
        }
    }
}
