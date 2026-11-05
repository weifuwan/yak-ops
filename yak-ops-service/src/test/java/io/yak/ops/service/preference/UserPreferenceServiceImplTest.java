package io.yak.ops.service.preference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.common.enums.UserPreferenceScene;
import io.yak.ops.dao.entity.UserPreference;
import io.yak.ops.dao.repository.UserPreferenceDao;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserPreferenceServiceImplTest {

    private UserPreferenceDao dao;
    private UserPreferenceService service;

    @BeforeEach
    void setUp() {
        dao = mock(UserPreferenceDao.class);
        service = new UserPreferenceServiceImpl(dao);
    }

    @Test
    void recordsUsageForSessionUserAndReturnsNewCount() {
        UserPreference saved = preference(12, UserPreferenceScene.DATASOURCE_CREATE_TYPE, "MYSQL", false, 0, 2);
        when(dao.recordUsage(12, "DATASOURCE_CREATE_TYPE", "MYSQL")).thenReturn(1);
        when(dao.find(12, "DATASOURCE_CREATE_TYPE", "MYSQL")).thenReturn(Optional.of(saved));

        assertEquals(2L, service.recordUsage(UserPreferenceScene.DATASOURCE_CREATE_TYPE, "MYSQL", 12).getUseCount());
        verify(dao).recordUsage(12, "DATASOURCE_CREATE_TYPE", "MYSQL");
    }

    @Test
    void favoritesUseExistingSortOrderAndDoNotReplaceUsage() {
        UserPreference previous = preference(7, UserPreferenceScene.PRODUCT_MENU, "operations", true, 3, 9);
        when(dao.find(7, "PRODUCT_MENU", "operations")).thenReturn(Optional.of(previous));
        when(dao.upsertFavorite(7, "PRODUCT_MENU", "operations", true, 3)).thenReturn(1);

        assertEquals(9L, service.updateFavorite(UserPreferenceScene.PRODUCT_MENU, "operations", true, 7).getUseCount());
        verify(dao).upsertFavorite(7, "PRODUCT_MENU", "operations", true, 3);
    }

    @Test
    void newFavoriteAppendsAfterCurrentFavorites() {
        UserPreference previous = preference(7, UserPreferenceScene.PRODUCT_MENU, "data-integration", true, 4, 0);
        UserPreference saved = preference(7, UserPreferenceScene.PRODUCT_MENU, "management", true, 5, 0);
        when(dao.find(7, "PRODUCT_MENU", "management"))
                .thenReturn(Optional.empty(), Optional.of(saved));
        when(dao.listByUserAndScene(7, "PRODUCT_MENU")).thenReturn(Arrays.asList(previous));
        when(dao.upsertFavorite(7, "PRODUCT_MENU", "management", true, 5)).thenReturn(1);

        assertEquals(5, service.updateFavorite(UserPreferenceScene.PRODUCT_MENU, "management", true, 7)
                .getSortOrder());
        verify(dao).upsertFavorite(7, "PRODUCT_MENU", "management", true, 5);
    }

    @Test
    void listingIsScopedToOneUserAndOneScene() {
        UserPreference row = preference(8, UserPreferenceScene.PRODUCT_MENU, "management", true, 1, 0);
        when(dao.listByUserAndScene(8, "PRODUCT_MENU")).thenReturn(Collections.singletonList(row));

        assertEquals("management", service.list(UserPreferenceScene.PRODUCT_MENU, 8).get(0).getItemKey());
        verify(dao).listByUserAndScene(8, "PRODUCT_MENU");
    }

    @Test
    void forbidsCrossSceneMutations() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateFavorite(UserPreferenceScene.DATASOURCE_CREATE_TYPE, "MYSQL", true, 8));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordUsage(UserPreferenceScene.PRODUCT_MENU, "management", 8));
        verifyNoInteractions(dao);
    }

    @Test
    void rejectsInvalidUserOrItemKey() {
        assertThrows(IllegalArgumentException.class,
                () -> service.list(UserPreferenceScene.PRODUCT_MENU, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordUsage(UserPreferenceScene.DATASOURCE_CREATE_TYPE, " ", 2));
        verifyNoInteractions(dao);
    }

    private UserPreference preference(int userId, UserPreferenceScene scene, String key,
                                      boolean favorite, int sortOrder, long count) {
        UserPreference value = new UserPreference();
        value.setUserId(userId);
        value.setScene(scene.name());
        value.setItemKey(key);
        value.setFavorite(favorite);
        value.setSortOrder(sortOrder);
        value.setUseCount(count);
        return value;
    }
}
