package io.yak.ops.dao.repository.security;

import io.yak.ops.common.PageData;
import io.yak.ops.dao.entity.security.UserEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;
import java.util.Optional;

/** User persistence boundary. */
public interface UserRepository extends BaseRepository<UserEntity, Long> {

    PageData<UserEntity> queryPage(
            Long id,
            String userName,
            String realName,
            long pageNo,
            long pageSize);

    Optional<UserEntity> queryByEmail(String email);

    Optional<UserEntity> queryByPhone(String phone);

    Optional<UserEntity> queryByUsername(String username);

    List<UserEntity> queryByIds(List<Long> userIds);

    List<UserEntity> queryByName(String name);

    int updatePassword(Long userId, String encodedPassword);
}
