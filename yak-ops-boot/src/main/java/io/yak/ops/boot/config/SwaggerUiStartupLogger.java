package io.yak.ops.boot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/** Logs the Swagger UI URL after the embedded servlet container has started. */
final class SwaggerUiStartupLogger implements ApplicationListener<ServletWebServerInitializedEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwaggerUiStartupLogger.class);
    private static final String DEFAULT_SWAGGER_UI_PATH = "/swagger-ui.html";

    private final Environment environment;

    SwaggerUiStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ServletWebServerInitializedEvent event) {
        ServletWebServerApplicationContext context = event.getApplicationContext();
        if (context.getServerNamespace() != null) return;

        String contextPath = context.getServletContext().getContextPath();
        int port = event.getWebServer().getPort();
        LOGGER.info("Swagger UI: {}", buildSwaggerUiUrl(environment, contextPath, port));
    }

    static String buildSwaggerUiUrl(Environment environment, String contextPath, int port) {
        String scheme = environment.getProperty("server.ssl.enabled", Boolean.class, false) ? "https" : "http";
        String host = resolveHost(environment.getProperty("server.address"));
        String swaggerUiPath = environment.getProperty("springdoc.swagger-ui.path", DEFAULT_SWAGGER_UI_PATH);

        return UriComponentsBuilder.newInstance()
                .scheme(scheme)
                .host(host)
                .port(port)
                .path(normalizePath(contextPath))
                .path(normalizePath(swaggerUiPath))
                .build()
                .toUriString();
    }

    private static String resolveHost(String configuredAddress) {
        if (!StringUtils.hasText(configuredAddress)
                || "0.0.0.0".equals(configuredAddress)
                || "::".equals(configuredAddress)
                || "[::]".equals(configuredAddress)) {
            return "localhost";
        }
        return configuredAddress;
    }

    private static String normalizePath(String path) {
        if (!StringUtils.hasText(path) || "/".equals(path)) return "";
        return path.startsWith("/") ? path : "/" + path;
    }
}
