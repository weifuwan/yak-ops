package io.yak.ops.spi.plugin;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.auto.service.AutoService;

public class PrioritySPIFactoryTest {

    @Test
    public void loadHighPriority() {
        PrioritySPIFactory<LoadHighPriorityConflictTestSPI> factory =
                new PrioritySPIFactory<>(LoadHighPriorityConflictTestSPI.class);
        Map<String, LoadHighPriorityConflictTestSPI> spiMap = factory.getSPIMap();
        Assertions.assertEquals(1, spiMap.get("A").getIdentify().getPriority());
    }

    @Test
    public void throwExceptionWhenPriorityIsSame() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new PrioritySPIFactory<>(ThrowExceptionConflictTestSPI.class);
        });
    }

    public interface LoadHighPriorityConflictTestSPI extends PrioritySPI {

    }

    @AutoService(LoadHighPriorityConflictTestSPI.class)
    public static class SPIA implements LoadHighPriorityConflictTestSPI {

        @Override
        public SPIIdentify getIdentify() {
            return SPIIdentify.builder().name("A").priority(0).build();
        }
    }

    @AutoService(LoadHighPriorityConflictTestSPI.class)
    public static class SPIAA implements LoadHighPriorityConflictTestSPI {

        @Override
        public SPIIdentify getIdentify() {
            return SPIIdentify.builder().name("A").priority(1).build();
        }
    }

    public interface ThrowExceptionConflictTestSPI extends PrioritySPI {

    }

    @AutoService(ThrowExceptionConflictTestSPI.class)
    public static class SPIB implements ThrowExceptionConflictTestSPI {

        @Override
        public SPIIdentify getIdentify() {
            return SPIIdentify.builder().name("B").priority(0).build();
        }
    }

    @AutoService(ThrowExceptionConflictTestSPI.class)
    public static class SPIBB implements ThrowExceptionConflictTestSPI {

        @Override
        public SPIIdentify getIdentify() {
            return SPIIdentify.builder().name("B").priority(0).build();
        }
    }

}
