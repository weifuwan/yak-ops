package io.yak.framework.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PageDataTest {

    @Test
    void shouldDefensivelyCopyRecords() {
        List<String> source = new ArrayList<>(List.of("a", "b"));

        PageData<String> page = new PageData<>(source, 2L, 1L, 1L, 20L);
        source.add("c");

        assertEquals(List.of("a", "b"), page.records());
        assertThrows(UnsupportedOperationException.class, () -> page.records().add("d"));
    }

    @Test
    void shouldCalculatePagesWithoutPersistenceFrameworkTypes() {
        PageData<String> page = PageData.of(List.of("a"), 21L, 2L, 10L);

        assertEquals(21L, page.total());
        assertEquals(3L, page.pages());
        assertEquals(2L, page.pageNo());
        assertEquals(10L, page.pageSize());

        assertEquals(0L, PageData.of(List.of(), 0L, 1L, 20L).pages());
        assertEquals(0L, PageData.of(List.of(), 10L, 1L, 0L).pages());
    }

    @Test
    void shouldMapRecordsAndKeepPaginationMetadata() {
        PageData<Integer> page = new PageData<>(List.of(1, 2), 12L, 6L, 3L, 2L);

        PageData<String> mapped = page.map(String::valueOf);

        assertEquals(List.of("1", "2"), mapped.records());
        assertEquals(12L, mapped.total());
        assertEquals(6L, mapped.pages());
        assertEquals(3L, mapped.pageNo());
        assertEquals(2L, mapped.pageSize());
    }

    @Test
    void shouldMapCovariantFunctionWithoutWildcardCaptureLeak() {
        PageData<Integer> page = PageData.of(List.of(1, 2), 2L, 1L, 20L);
        Function<Number, StringBuilder> mapper =
                value -> new StringBuilder(String.valueOf(value));

        PageData<CharSequence> mapped = page.map(mapper);

        assertEquals(
                List.of("1", "2"),
                mapped.records().stream().map(CharSequence::toString).toList());
        assertEquals(2L, mapped.total());
        assertEquals(1L, mapped.pages());
        assertEquals(1L, mapped.pageNo());
        assertEquals(20L, mapped.pageSize());
    }

    @Test
    void shouldKeepExistingHttpPagingShapeWhenConverting() {
        PagingData<String> pagingData =
                PagingData.from(new PageData<>(List.of("a"), 21L, 3L, 2L, 10L));

        assertEquals(List.of("a"), pagingData.getBizData());
        assertEquals(21L, pagingData.getPagination().getTotal());
        assertEquals(3L, pagingData.getPagination().getPages());
        assertEquals(2L, pagingData.getPagination().getPageNo());
        assertEquals(10L, pagingData.getPagination().getPageSize());
    }

    @Test
    void sharedPageDataShouldNotBeExtendedIntoBusinessSpecificAliases() {
        assertTrue(Modifier.isFinal(PageData.class.getModifiers()));
    }

    @Test
    void pagingDataPublicConstructorsShouldNotDependOnPersistenceFrameworks() {
        for (Constructor<?> constructor : PagingData.class.getConstructors()) {
            boolean hasPersistenceType =
                    Arrays.stream(constructor.getParameterTypes())
                            .map(Class::getName)
                            .anyMatch(name ->
                                    name.startsWith("com.baomidou.")
                                            || name.startsWith("org.springframework.jdbc.")
                                            || name.startsWith("jakarta.persistence."));
            assertFalse(
                    hasPersistenceType,
                    () -> "PagingData constructor must stay persistence-framework neutral: " + constructor);
        }
    }
}
