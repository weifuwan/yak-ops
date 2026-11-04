package io.yak.ops.api.service;

import io.yak.ops.api.dto.FavTaskDto;
import io.yak.ops.dao.entity.User;

import java.util.List;

public interface FavTaskService {

    List<FavTaskDto> getFavTaskList(User loginUser);

    boolean deleteFavTask(User loginUser, String taskType);

    int addFavTask(User loginUser, String taskType);
}
