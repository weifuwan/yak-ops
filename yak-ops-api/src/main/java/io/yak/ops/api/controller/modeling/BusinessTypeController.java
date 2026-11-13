package io.yak.ops.api.controller.modeling;

import io.yak.ops.modeling.api.dto.businesstype.BusinessTypeDTO;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.modeling.api.vo.businesstype.BusinessTypeVO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;
import io.yak.ops.modeling.application.service.BusinessTypeService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/projects/{projectCode}/modeling/business-type")
public class BusinessTypeController extends BaseController {

    @Resource
    private BusinessTypeService businessTypeService;

    /**
     * 新增业务分类
     */
    @PostMapping
    public Result<String> create(@RequestBody BusinessTypeDTO businessType) {
        return Result.success(businessTypeService.create(businessType));
    }

    /**
     * 修改业务分类
     */
    @PutMapping
    public Result<BusinessTypeVO> update(@RequestBody BusinessTypeDTO businessType) {
        return Result.success(businessTypeService.updateById(businessType));
    }

    /**
     * 删除业务分类
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable String id) {
        return Result.success(businessTypeService.delete(id));
    }

    /**
     * 根据ID查询业务分类
     */
    @GetMapping("/{id}")
    public Result<BusinessTypeVO> selectById(@PathVariable String id) {
        return Result.success(businessTypeService.selectById(id));
    }

    /**
     * 分页查询业务分类
     */
    @PostMapping(value = "page")
    public Result<PageInfo<BusinessTypeVO>> paging(@RequestBody BusinessTypeDTO businessTypeDTO) {
        checkPageParams(businessTypeDTO.getPageNo(), businessTypeDTO.getPageSize());
        return Result.success(PageInfo.of(businessTypeService.paging(businessTypeDTO)));
    }

    /**
     * 树形结构查询业务分类
     */
    @GetMapping(value = "tree")
    public Result<List<TreeVO>> tree(@RequestParam("keyword") String keyword) {
        return Result.success(businessTypeService.tree(keyword, null));
    }

    /**
     * 查询业务分类所有数据
     */
    @GetMapping(value = "all")
    public Result<List<BusinessTypeVO>> all() {
        return Result.success(businessTypeService.all());
    }

    /**
     * 根据业务分类，查询对应的数据域
     */
    @GetMapping(value = "/select/related-domain/{id}")
    public Result<List<DataDomainVO>> selectTypeData(@PathVariable("id") String id) {
        return Result.success(businessTypeService.selectTypeData(id));
    }

    /**
     * 更新业务分类 - 数据域关系
     */
    @PutMapping(value = "/update/related-domain/{id}")
    public Result<Boolean> updateTypeData(@PathVariable("id") String id, @RequestBody List<String> dataDomainIds) {
        return Result.success(businessTypeService.updateTypeData(id, dataDomainIds));
    }

    /**
     * 删除业务分类 - 数据域关系
     */
    @DeleteMapping(value = "/delete/related-domain/{id}")
    public Result<Boolean> deleteTypeData(@PathVariable("id") String id, @RequestParam("dataDomainId") String dataDomainId) {
        return Result.success(businessTypeService.deleteTypeData(id, dataDomainId));
    }

    /**
     * 获取业务分类 - 数据域选项
     */
    @GetMapping(value = "/option/related-domain/{id}")
    public Result<List<OptionVO>> optionRelatedDomain(@PathVariable("id") String id, @RequestParam("checked") boolean checked) {
        return Result.success(businessTypeService.optionRelatedDomain(id, checked));
    }

    /**
     * 加载所有数据域
     */
    @PutMapping(value = "/loadall/related-domain/{id}")
    public Result<Boolean> onLoadAllDomains(@PathVariable("id") String id) {
        return Result.success(businessTypeService.onLoadAllDomains(id));
    }
}
