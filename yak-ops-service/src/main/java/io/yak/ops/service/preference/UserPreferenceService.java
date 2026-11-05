package io.yak.ops.service.preference;

import io.yak.ops.common.enums.UserPreferenceScene;
import io.yak.ops.common.model.UserPreferenceRecord;

import java.util.List;

/** Per-user preferences; the caller supplies only the authenticated session user. */
public interface UserPreferenceService {

    List<UserPreferenceRecord> list(UserPreferenceScene scene, int userId);

    UserPreferenceRecord updateFavorite(UserPreferenceScene scene, String itemKey, boolean favorite, int userId);

    UserPreferenceRecord recordUsage(UserPreferenceScene scene, String itemKey, int userId);
}
