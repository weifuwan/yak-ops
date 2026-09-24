package io.yak.framework.security.autoconfigure;

import io.yak.framework.security.controller.v1.OplogController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "yak.security",
        name = {
                "database-enabled",
                "web-enabled",
                "audit-enabled"
        },
        havingValue = "true",
        matchIfMissing = true
)
@Import(OplogController.class)
public class YakSecurityAuditConfiguration {
}