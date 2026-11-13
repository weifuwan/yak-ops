package io.yak.ops.api.controller.modeling;

import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.modeling.api.vo.businessprocess.BusinessProcessVO;
import io.yak.ops.modeling.api.vo.businessprocess.CascaderTreeVO;
import io.yak.ops.modeling.application.service.BusinessProcessService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/projects/{projectCode}/modeling/business-process")
public class BusinessProcessController extends BaseController {

    @Resource
    private BusinessProcessService businessProcessService;

    /**
     * 新增业务过程
     */
    @PostMapping
    public Result<String> create(@RequestBody BusinessProcessDTO dto) {
        return Result.success(businessProcessService.create(dto));
    }

    /**
     * 修改业务过程
     */
    @PutMapping
    public Result<BusinessProcessVO> update(@RequestBody BusinessProcessDTO dto) {
        return Result.success(businessProcessService.updateById(dto));
    }

    /**
     * 删除业务过程
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable String id) {
        return Result.success(businessProcessService.delete(id));
    }

    /**
     * 根据ID查询业务过程
     */
    @GetMapping("/{id}")
    public Result<BusinessProcessVO> selectById(@PathVariable String id) {
        return Result.success(businessProcessService.selectById(id));
    }

    /**
     * 分页查询业务过程
     */
    @PostMapping(value = "page")
    public Result<PageInfo<BusinessProcessVO>> paging(@RequestBody BusinessProcessDTO dto) {
        checkPageParams(dto.getPageNo(), dto.getPageSize());
        return Result.success(PageInfo.of(businessProcessService.paging(dto)));
    }

    /**
     * 分页查询业务过程
     */
    @GetMapping(value = "all")
    public Result<List<BusinessProcessVO>> all(BusinessProcessDTO dto) {
        return Result.success(businessProcessService.all(dto));
    }

    /**
     * 根据业务分类ID获取业务过程选择树
     */
    @GetMapping("/option/cascader")
    public Result<List<CascaderTreeVO>> getTreeOption(@RequestParam(name = "busTypeId",required = false) String busTypeId)  {
        return Result.success(businessProcessService.treeList(busTypeId));
    }
}
