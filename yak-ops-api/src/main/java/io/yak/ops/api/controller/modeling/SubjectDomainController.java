package io.yak.ops.api.controller.modeling;

import io.yak.ops.modeling.api.dto.subjectdomain.SubjectDomainDTO;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.modeling.api.vo.subjectdomain.SubjectDomainVO;
import io.yak.ops.modeling.api.vo.tree.TreeTypeValueVO;
import io.yak.ops.modeling.application.service.SubjectDomainService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/projects/{projectCode}/modeling/subject-domain")
public class SubjectDomainController extends BaseController {

    @Resource
    private SubjectDomainService subjectDomainService;

    /**
     * 新增主题域
     */
    @PostMapping
    public Result<String> create(@RequestBody SubjectDomainDTO dto) {
        return Result.success(subjectDomainService.create(dto));
    }

    /**
     * 修改主题域
     */
    @PutMapping
    public Result<SubjectDomainVO> update(@RequestBody SubjectDomainDTO dto) {
        return Result.success(SubjectDomainService.updateById(dto));
    }

    /**
     * 删除主题域
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable String id) {
        return Result.success(SubjectDomainService.delete(id));
    }

    /**
     * 根据ID查询主题域
     */
    @GetMapping("/{id}")
    public Result<SubjectDomainVO> selectById(@PathVariable String id) {
        return Result.success(SubjectDomainService.selectById(id));
    }

    /**
     * 分页查询主题域
     */
    @PostMapping(value = "page")
    public Result<PageInfo<SubjectDomainVO>> paging(@RequestBody SubjectDomainDTO dto) {
        checkPageParams(dto.getPageNo(), dto.getPageSize());
        return Result.success(PageInfo.of(SubjectDomainService.paging(dto)));
    }

    /**
     * 树形结构查询主题域
     */
    @GetMapping(value = "tree")
    public Result<List<TreeTypeValueVO>> tree() {
        return Result.success(SubjectDomainService.tree());
    }

    /**
     * 查询所有主题域数据
     */
    @PostMapping(value = "all")
    public Result<List<SubjectDomainVO>> all() {
        return Result.success(SubjectDomainService.all());
    }
}
