package io.yak.ops.boot.controller;

import io.yak.ops.common.Result;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Simple endpoint used to verify the Yak Ops and Yak Framework integration. */
@RestController
@RequestMapping("/api/test")
public class TestController {

    @Value("${spring.application.name:yak-ops}")
    private String applicationName;

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", applicationName);
        data.put("status", "UP");
        data.put("framework", "yak-framework");
        data.put("timestamp", Instant.now().toString());
        return Result.success(data);
    }
}
