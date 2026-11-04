package io.yak.ops.plugin.task.api.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class DependentItemTest {

    @ParameterizedTest
    @CsvSource({
            "123, 2001-123-hour-currentHour, hour, currentHour",
            "0, 2001-0-week-lastWeek, week, lastWeek",
            "-1, 2001--1-day-today, day, today",
            "-9223372036854775808, 2001--9223372036854775808-month-lastMonthEnd, month, lastMonthEnd"
    })
    public void testKeyRoundTrip(long depTaskCode, String key, String cycle, String dateValue) {
        DependentItem dependentItem = new DependentItem();
        dependentItem.setDefinitionCode(2001);
        dependentItem.setDepTaskCode(depTaskCode);
        dependentItem.setCycle(cycle);
        dependentItem.setDateValue(dateValue);

        Assertions.assertEquals(key, dependentItem.getKey());

        DependentItem parsedDependentItem = new DependentItem().fromKey(key);
        Assertions.assertEquals(dependentItem.getDefinitionCode(), parsedDependentItem.getDefinitionCode());
        Assertions.assertEquals(dependentItem.getDepTaskCode(), parsedDependentItem.getDepTaskCode());
        Assertions.assertEquals(dependentItem.getCycle(), parsedDependentItem.getCycle());
        Assertions.assertEquals(dependentItem.getDateValue(), parsedDependentItem.getDateValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "invalid-key",
            "2001-1-day",
            "2001-1-day-",
            "2001-1-day-today-extra",
            "2001---1-day-today"
    })
    public void testFromKeyWithInvalidFormat(String key) {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new DependentItem().fromKey(key));
    }
}
