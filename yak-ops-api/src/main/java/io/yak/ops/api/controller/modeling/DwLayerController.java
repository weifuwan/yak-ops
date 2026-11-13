package io.yak.ops.api.controller.modeling;

import lombok.extern.slf4j.Slf4j;
import io.yak.ops.modeling.api.dto.dwlayer.DwLayerDTO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.api.utils.Result;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.dao.entity.enums.DwModelEnum;
import io.yak.ops.modeling.application.service.DwLayerService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/projects/{projectCode}/modeling/dw-layer")
public class DwLayerController {

    @Resource
    private DwLayerService dwLayerService;

    /**
     * 新增数仓分层
     */
    @PostMapping
    public Result<String> create(@RequestBody DwLayerDTO dto) {
        return Result.success(dwLayerService.create(dto));
    }

    /**
     * 修改数仓分层
     */
    @PutMapping
    public Result<DwLayerVO> update(@RequestBody DwLayerDTO dto) {
        return Result.success(dwLayerService.updateById(dto));
    }

    /**
     * 删除数仓分层
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable String id) {
        return Result.success(dwLayerService.delete(id));
    }

    /**
     * 根据ID查询数仓分层
     */
    @GetMapping("/{id}")
    public Result<DwLayerVO> selectById(@PathVariable String id) {
        return Result.success(dwLayerService.selectById(id));
    }

    /**
     * 分页查询数仓分层
     */
    @PostMapping(value = "page")
    public PaginationResult<DwLayerVO> paging(@RequestBody DwLayerDTO DwLayerDTO) {
        return dwLayerService.paging(DwLayerDTO);
    }

    /**
     * 分页查询数仓分层
     */
    @GetMapping(value = "all")
    public Result<List<DwLayerVO>> all(DwLayerDTO dto) {
        return Result.success(dwLayerService.all(dto));
    }

    /**
     * 数仓分层选项
     */
    @PostMapping(value = "option")
    public Result<List<OptionVO>> option(@RequestBody List<String> dwType) {
        return Result.success(dwLayerService.option(dwType));
    }

    /**
     * 通过表获取数仓分层选项
     */
    @GetMapping(value = "option/modal-type")
    public Result<List<OptionVO>> optionByModalType(@RequestParam("modalType") DwModelEnum modalType) {
        return Result.success(dwLayerService.optionByModalType(modalType));
    }
}
