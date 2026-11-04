package io.yak.ops.api.service;

import io.yak.ops.api.utils.Result;
import io.yak.ops.dao.entity.User;

public interface ProjectPreferenceService {

    Result updateProjectPreference(User loginUser, long projectCode, String preferences);

    Result queryProjectPreferenceByProjectCode(User loginUser, long projectCode);

    Result enableProjectPreference(User loginUser, long projectCode, int state);
}
