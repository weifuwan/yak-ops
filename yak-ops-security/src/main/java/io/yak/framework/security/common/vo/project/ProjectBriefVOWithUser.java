package io.yak.framework.security.common.vo.project;

import lombok.Data;

import io.yak.framework.security.common.vo.user.UserBasicVO;
import java.util.List;
/**
 * 包含用户信息的项目简要视图对象。
 *
 * @author weifuwan
 */
@Data
public class ProjectBriefVOWithUser {
  /** 主键标识。 */
  private Long id;
  /** 项目编码。 */
  private String projectCode;
  /** 项目名称。 */
  private String projectName;
  /** 项目负责人列表。 */
  private List<UserBasicVO> ownerList;
  /** 用户列表。 */
  private List<UserBasicVO> userList;

  public Long getId() { return this.id; }

  public String getProjectCode() { return this.projectCode; }

  public String getProjectName() { return this.projectName; }

  public List<UserBasicVO> getOwnerList() { return this.ownerList; }

  public List<UserBasicVO> getUserList() { return this.userList; }

  public void setId(Long id) { this.id = id; }

  public void setProjectCode(String projectCode) {
    this.projectCode = projectCode;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public void setOwnerList(List<UserBasicVO> ownerList) {
    this.ownerList = ownerList;
  }

  public void setUserList(List<UserBasicVO> userList) {
    this.userList = userList;
  }

}
