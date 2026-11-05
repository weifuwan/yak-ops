package io.yak.ops.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.UserPreferenceScene;
import io.yak.ops.common.model.UserPreferenceRecord;
import io.yak.ops.dao.entity.User;
import io.yak.ops.service.preference.UserPreferenceService;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserPreferenceControllerTest {

    private UserPreferenceService service;
    private MockMvc mvc;
    private User sessionUser;

    @BeforeEach
    void setUp() {
        service = mock(UserPreferenceService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserPreferenceController(service)).build();
        sessionUser = new User();
        sessionUser.setId(7);
    }

    @Test
    void queriesOnlyTheAuthenticatedUser() throws Exception {
        when(service.list(UserPreferenceScene.PRODUCT_MENU, 7)).thenReturn(Collections.emptyList());

        mvc.perform(get("/user-preferences")
                .requestAttr(Constants.SESSION_USER, sessionUser)
                .param("scene", "PRODUCT_MENU")
                .param("userId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(service).list(UserPreferenceScene.PRODUCT_MENU, 7);
    }

    @Test
    void updatesFavoriteUsingTheSessionIdentity() throws Exception {
        UserPreferenceRecord saved = preference(UserPreferenceScene.PRODUCT_MENU, "management");
        when(service.updateFavorite(UserPreferenceScene.PRODUCT_MENU, "management", true, 7))
                .thenReturn(saved);

        mvc.perform(put("/user-preferences/PRODUCT_MENU/management/favorite")
                .requestAttr(Constants.SESSION_USER, sessionUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"favorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemKey").value("management"));

        verify(service).updateFavorite(UserPreferenceScene.PRODUCT_MENU, "management", true, 7);
    }

    @Test
    void recordsUsageUsingTheSessionIdentity() throws Exception {
        UserPreferenceRecord saved = preference(UserPreferenceScene.DATASOURCE_CREATE_TYPE, "MYSQL");
        when(service.recordUsage(UserPreferenceScene.DATASOURCE_CREATE_TYPE, "MYSQL", 7))
                .thenReturn(saved);

        mvc.perform(post("/user-preferences/DATASOURCE_CREATE_TYPE/MYSQL/use")
                .requestAttr(Constants.SESSION_USER, sessionUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemKey").value("MYSQL"));

        verify(service).recordUsage(UserPreferenceScene.DATASOURCE_CREATE_TYPE, "MYSQL", 7);
    }

    @Test
    void rejectsMissingSessionUser() throws Exception {
        mvc.perform(get("/user-preferences").param("scene", "PRODUCT_MENU"))
                .andExpect(status().isBadRequest());
    }

    private UserPreferenceRecord preference(UserPreferenceScene scene, String itemKey) {
        UserPreferenceRecord saved = new UserPreferenceRecord();
        saved.setScene(scene);
        saved.setItemKey(itemKey);
        saved.setFavorite(false);
        saved.setSortOrder(0);
        saved.setUseCount(1L);
        return saved;
    }
}
