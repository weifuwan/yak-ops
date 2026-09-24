package io.yak.framework.security.common.vo.project;

import lombok.Data;

import io.yak.framework.security.common.vo.dept.DeptBriefVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import java.util.Date;
import java.util.List;
/**
 * 项目详情视图对象。
 *
 * @author weifuwan
 */
@Data
public class ProjectVO {
  /** 主键标识。 */
  private Long id;
  /** 项目编码。 */
  private String projectCode;
  /** 项目名称。 */
  private String projectName;
  /** 用户列表。 */
  private List<UserBriefVO> userList;
  /** 项目负责人列表。 */
  private List<UserBriefVO> ownerList;
  /** 描述信息。 */
  private String description;
  /** 项目是否运行中。 */
  private Boolean running;
  /** 部门列表。 */
  private List<DeptBriefVO> deptList;
  /** 部门标识。 */
  private Long deptId;
  /** 创建时间。 */
  private Date createTime;
  /** 更新时间。 */
  private Date updateTime;

}
