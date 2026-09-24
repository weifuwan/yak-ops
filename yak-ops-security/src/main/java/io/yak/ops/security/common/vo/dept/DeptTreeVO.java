package io.yak.ops.security.common.vo.dept;

import lombok.Data;

import java.util.List;
/**
 * 部门树节点视图对象。
 *
 * @author weifuwan
 */
@Data
public class DeptTreeVO {
  /** 主键标识。 */
  private Long id;
  /** 部门名称。 */
  private String deptName;
  /** 描述信息。 */
  private String description;
  /** 父节点标识。 */
  private Long parentId;
  /** 是否为叶子节点。 */
  private Boolean leaf;
  /** 子节点列表。 */
  private List<DeptTreeVO> childList;

  public DeptTreeVO() {}

  DeptTreeVO(Long id, String deptName, String description, Long parentId,
             Boolean leaf, List<DeptTreeVO> childList) {
    this.id = id;
    this.deptName = deptName;
    this.description = description;
    this.parentId = parentId;
    this.leaf = leaf;
    this.childList = childList;
  }

  public static DeptTreeVOBuilder builder() { return new DeptTreeVOBuilder(); }

  /**
   * 部门树节点构建器。
   *
   * @author weifuwan
   */
  public static class DeptTreeVOBuilder {
    /** 主键标识。 */
    private Long id;
    /** 部门名称。 */
    private String deptName;
    /** 描述信息。 */
    private String description;
    /** 父节点标识。 */
    private Long parentId;
    /** 是否为叶子节点。 */
    private Boolean leaf;
    /** 子节点列表。 */
    private List<DeptTreeVO> childList;

    DeptTreeVOBuilder() {}

    public DeptTreeVOBuilder id(Long id) {
      this.id = id;
      return this;
    }

    public DeptTreeVOBuilder deptName(String deptName) {
      this.deptName = deptName;
      return this;
    }

    public DeptTreeVOBuilder description(String description) {
      this.description = description;
      return this;
    }

    public DeptTreeVOBuilder parentId(Long parentId) {
      this.parentId = parentId;
      return this;
    }

    public DeptTreeVOBuilder leaf(Boolean leaf) {
      this.leaf = leaf;
      return this;
    }

    public DeptTreeVOBuilder childList(List<DeptTreeVO> childList) {
      this.childList = childList;
      return this;
    }

    public DeptTreeVO build() {
      return new DeptTreeVO(this.id, this.deptName, this.description,
                            this.parentId, this.leaf, this.childList);
    }

    public String toString() {
      return "DeptTreeVO.DeptTreeVOBuilder(id=" + this.id +
          ", deptName=" + this.deptName +
          ", description=" + this.description +
          ", parentId=" + this.parentId + ", leaf=" + this.leaf +
          ", childList=" + this.childList + ")";
    }
  }
}
