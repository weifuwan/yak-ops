package io.yak.ops.spi.plugin;

public interface PrioritySPI extends Comparable<Integer> {

    /**
     * The SPI identify, if the two plugin has the same name, will load the high priority.
     * If the priority and name is all same, will throw <code>IllegalArgumentException</code>
     * @return
     */
    SPIIdentify getIdentify();

    @Override
    default int compareTo(Integer o) {
        return Integer.compare(getIdentify().getPriority(), o);
    }

}
