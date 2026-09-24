package io.yak.ops.security.common.vo.user;

import io.yak.ops.security.common.vo.project.ProjectBriefVO;
import io.yak.ops.security.common.vo.role.RoleBriefVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录用户身份。
 *
 * @author weifuwan
 */
@Data
public class CurrentUserVO {

    private Long id;

    private String userName;

    private String realName;

    private Long deptId;

    private String phone;

    private String email;

    /** 当前用户拥有的角色。 */
    private List<RoleBriefVO> roleList =
            new ArrayList<>();

    /**
     * 当前用户拥有的权限编码。
     */
    private List<String> permissionCodes =
            new ArrayList<>();

    /**
     * 当前用户通过角色获得的菜单编码。
     *
     * <p>null 表示当前部署未启用菜单授权能力；空列表表示已启用但用户没有菜单。
     */
    private List<String> menuCodes;

    /**
     * 当前身份可进入的启用项目。
     *
     * <p>超级管理员可进入当前应用全部启用项目；普通用户仅返回其负责人或成员关系下的启用项目。
     */
    private List<ProjectBriefVO> projectList =
            new ArrayList<>();
}
