package io.yak.ops.boot.controller;

import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import io.yak.ops.task.execution.TaskExecutionService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP admission for independently executable Task Definitions. */
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/task-definitions")
public class TaskExecutionController {

    @Resource
    private TaskExecutionService service;

    @Resource
    private AuthenticationManager authenticationManager;

    @PostMapping("/{id}/instances")
    public ResponseEntity<Result<SubmissionResult>> run(
            @PathVariable String id, @RequestHeader(value = "Idempotency-Key", required = false) String requestId) {
        SubmissionResult result = service.submitManual(id, requestId, authenticationManager.getLoginUserId());
        return ResponseEntity.accepted().body(Result.success(result));
    }
}
