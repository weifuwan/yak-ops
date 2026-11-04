package io.yak.ops.api.configuration;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(value = "api")
public class ApiConfig implements Validator {

    private String baseUrl;
    private String uiUrl;
    private boolean auditEnable = false;

    private TrafficConfiguration trafficControl = new TrafficConfiguration();

    private PythonGatewayConfiguration pythonGateway = new PythonGatewayConfiguration();

    @Override
    public boolean supports(Class<?> clazz) {
        return ApiConfig.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        validatePythonGateway(errors);
        printConfig();
    }

    private void validatePythonGateway(Errors errors) {
        if (pythonGateway.isEnabled() && StringUtils.isEmpty(pythonGateway.getAuthToken())) {
            errors.rejectValue("pythonGateway.auth-token", null, "should not be empty when enabled is true");
        }
    }

    private void printConfig() {
        log.info("API config: baseUrl -> {} ", baseUrl);
        log.info("API config: uiUrl -> {} ", uiUrl);
        log.info("API config: auditEnable -> {} ", auditEnable);
        log.info("API config: trafficControl -> {} ", trafficControl);
        log.info("API config: pythonGateway -> {} ", pythonGateway);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficConfiguration {

        private boolean globalSwitch = false;
        private Integer maxGlobalQpsRate = 300;
        private boolean tenantSwitch = false;
        private Integer defaultTenantQpsRate = 10;
        private Map<String, Integer> customizeTenantQpsRate = new HashMap<>();
    }

    @ToString(exclude = "authToken")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PythonGatewayConfiguration {

        private boolean enabled = false;
        private String gatewayServerAddress = "0.0.0.0";
        private int gatewayServerPort = 25333;
        private String pythonAddress = "127.0.0.1";
        private int pythonPort = 25334;
        private int connectTimeout = 0;
        private int readTimeout = 0;
        private String authToken = "jwUDzpLsNKEFER4*a8gruBH_GsAurNxU7A@Xc";
    }

}
