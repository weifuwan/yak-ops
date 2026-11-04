package io.yak.ops.api.service.impl;

import io.yak.ops.api.configuration.TaskTypeConfiguration;
import io.yak.ops.api.dto.FavTaskDto;
import io.yak.ops.api.service.FavTaskService;
import io.yak.ops.dao.entity.FavTask;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.mapper.FavTaskMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

@Service
public class FavTaskServiceImpl extends BaseServiceImpl implements FavTaskService {

    @Resource
    private TaskTypeConfiguration taskTypeConfiguration;
    @Resource
    private FavTaskMapper favMapper;

    @Override
    public List<FavTaskDto> getFavTaskList(User loginUser) {
        Set<String> userFavTaskTypes = favMapper.getUserFavTaskTypes(loginUser.getId());

        List<FavTaskDto> defaultTaskTypes = taskTypeConfiguration.getDefaultTaskTypes();
        List<FavTaskDto> result = new ArrayList<>();
        // clone default list and modify fav task type flag
        defaultTaskTypes.forEach(e -> {
            try {
                FavTaskDto clone = (FavTaskDto) e.clone();
                if (userFavTaskTypes.contains(clone.getTaskType())) {
                    clone.setCollection(true);
                }
                result.add(clone);
            } catch (CloneNotSupportedException ex) {
                throw new RuntimeException(ex);
            }
        });
        return result;
    }

    @Override
    public boolean deleteFavTask(User loginUser, String taskType) {
        return favMapper.deleteUserFavTask(loginUser.getId(), taskType);
    }

    @Override
    public int addFavTask(User loginUser, String taskType) {
        favMapper.deleteUserFavTask(loginUser.getId(), taskType);
        return favMapper.insert(new FavTask(null, taskType, loginUser.getId()));
    }
}
