package io.yak.ops.api.controller;

import static io.yak.ops.api.enums.Status.LIST_PAGING_ALERT_RECORD_ERROR;

import io.yak.ops.api.exceptions.ApiException;
import io.yak.ops.api.service.AlertService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.AlertStatus;
import io.yak.ops.common.enums.AlertType;
import io.yak.ops.dao.entity.Alert;
import io.yak.ops.dao.entity.User;
import io.yak.ops.plugin.task.api.utils.ParameterUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "ALERT_TAG")
@RestController
@RequestMapping("/projects/{projectCode}/alerts")
public class AlertController extends BaseController {

    @Autowired
    private AlertService alertService;

    @Operation(summary = "listAlerts", description = "QUERY_ALERT_RECORD_LIST_NOTES")
    @Parameters({
            @Parameter(name = "pageNo", description = "PAGE_NO", required = true, schema = @Schema(implementation = int.class, example = "1")),
            @Parameter(name = "pageSize", description = "PAGE_SIZE", required = true, schema = @Schema(implementation = int.class, example = "20")),
            @Parameter(name = "alertStatus", description = "ALERT_STATUS", schema = @Schema(implementation = AlertStatus.class)),
            @Parameter(name = "alertType", description = "ALERT_TYPE", schema = @Schema(implementation = AlertType.class)),
            @Parameter(name = "keyword", description = "KEYWORD", schema = @Schema(implementation = String.class))
    })
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @ApiException(LIST_PAGING_ALERT_RECORD_ERROR)
    public Result<PageInfo<Alert>> listAlerts(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(name = "projectCode", description = "PROJECT_CODE", required = true) @PathVariable long projectCode,
            @RequestParam("pageNo") int pageNo,
            @RequestParam("pageSize") int pageSize,
            @RequestParam(value = "alertStatus", required = false) AlertStatus alertStatus,
            @RequestParam(value = "alertType", required = false) AlertType alertType,
            @RequestParam(value = "keyword", required = false) String keyword) {
        checkPageParams(pageNo, pageSize);
        keyword = ParameterUtils.handleEscapes(keyword);
        return Result.success(alertService.listPaging(
                loginUser,
                projectCode,
                alertStatus,
                alertType,
                keyword,
                pageNo,
                pageSize));
    }
}
