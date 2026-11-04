package io.yak.ops.api.vo;

import io.yak.ops.dao.entity.User;

import lombok.Data;

@Data
public class UserSimpleInfoVO {

    private Integer id;

    private String userName;

    public UserSimpleInfoVO(User user) {
        this.id = user.getId();
        this.userName = user.getUserName();
    }
}
