package io.yak.ops.api.controller.modeling;

import io.yak.ops.modeling.api.dto.datadomain.DataDomainDTO;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;
import io.yak.ops.modeling.application.service.DataDomainService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/projects/{projectCode}/modeling/data-domain")
public class DataDomainController extends BaseController {

    @Resource
    private DataDomainService dataDomainService;

    @PostMapping
    public Result<String> create(@RequestBody DataDomainDTO dto) {
        return Result.success(dataDomainService.create(dto));
    }

    @PutMapping
    public Result<DataDomainVO> update(@RequestBody DataDomainDTO DataDomain) {
        return Result.success(dataDomainService.updateById(dto));
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable String id) {
        return Result.success(dataDomainService.delete(id));
    }

    @GetMapping("/{id}")
    public Result<DataDomainVO> selectById(@PathVariable String id) {
        return Result.success(dataDomainService.selectById(id));
    }

    @PostMapping(value = "page")
    public Result<PageInfo<DataDomainVO>> paging(@RequestBody DataDomainDTO dto) {
        checkPageParams(DataDomainDTO.getPageNo(), DataDomainDTO.getPageSize());
        return Result.success(PageInfo.of(dataDomainService.paging(dto)));
    }

    @GetMapping(value = "all")
    public Result<List<DataDomainVO>> all() {
        return Result.success(dataDomainService.all());
    }


}
