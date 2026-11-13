package io.yak.ops.api.controller.modeling;

import lombok.extern.slf4j.Slf4j;
import io.yak.ops.modeling.api.dto.datamart.DataMartDTO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.api.utils.Result;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;
import io.yak.ops.modeling.application.service.DataMartService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/projects/{projectCode}/modeling/data-mart")
public class DataMartController {

    @Resource
    private DataMartService DataMartService;

    /**
     * 新增数据集市
     */
    @PostMapping
    public Result<String> create(@RequestBody DataMartDTO dto) {
        return Result.success(DataMartService.create(dto));
    }

    /**
     * 修改数据集市
     */
    @PutMapping
    public Result<DataMartVO> update(@RequestBody DataMartDTO dto) {
        return Result.success(DataMartService.updateById(dto));
    }

    /**
     * 删除数据集市
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable String id) {
        return Result.success(DataMartService.delete(id));
    }

    /**
     * 根据ID查询数据集市
     */
    @GetMapping("/{id}")
    public Result<DataMartVO> selectById(@PathVariable String id) {
        return Result.success(DataMartService.selectById(id));
    }

    /**
     * 分页查询数据集市
     */
    @PostMapping(value = "page")
    public PaginationResult<DataMartVO> paging(@RequestBody DataMartDTO dto) {
        return DataMartService.paging(dto);
    }

    /**
     * 树形结构查询数据集市
     */
    @GetMapping(value = "tree")
    public Result<List<TreeVO>> tree(@RequestParam("keyword") String keyword) {
        return Result.success(DataMartService.tree(keyword));
    }

    /**
     * 查询所有数据集市数据
     */
    @GetMapping(value = "all")
    public Result<List<DataMartVO>> all() {
        return Result.success(DataMartService.all());
    }
}
