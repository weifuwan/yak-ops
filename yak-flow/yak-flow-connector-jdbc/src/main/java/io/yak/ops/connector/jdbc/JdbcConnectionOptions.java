package io.yak.ops.connector.jdbc;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable JDBC connection configuration shared by Catalog, Source and Sink.
 *
 * <p>Connection settings are execution inputs, not task-definition or checkpoint state.
 * The connection provider owns driver loading and the lifetime of opened connections.
 */
public final class JdbcConnectionOptions implements java.io.Serializable {
    public static final Option<String> URL =
            Options.key("connection.url").stringType().noDefaultValue();
    public static final Option<String> DRIVER_CLASS =
            Options.key("connection.driver-class").stringType().noDefaultValue();
    public static final Option<String> DRIVER_ID =
            Options.key("connection.driver-id").stringType().noDefaultValue();
    public static final Option<String> USERNAME =
            Options.key("connection.username").stringType().noDefaultValue();
    public static final Option<String> PASSWORD =
            Options.key("connection.password").stringType().noDefaultValue();
    public static final Option<String> HOST =
            Options.key("connection.host").stringType().noDefaultValue();
    public static final Option<Integer> PORT =
            Options.key("connection.port").intType().noDefaultValue();
    public static final Option<String> DATABASE_TYPE =
            Options.key("connection.database-type").stringType().noDefaultValue();
    public static final Option<Map<String, String>> PROPERTIES =
            Options.key("connection.properties").mapType().defaultValue(Map.of());
    public static final Option<Integer> TIMEOUT =
            Options.key("connection.timeout-seconds").intType().defaultValue(30);
    public static final Option<Boolean> SSH_ENABLED =
            Options.key("connection.ssh.enabled").booleanType().defaultValue(false);
    public static final Option<String> SSH_HOST =
            Options.key("connection.ssh.host").stringType().noDefaultValue();
    public static final Option<Integer> SSH_PORT =
            Options.key("connection.ssh.port").intType().defaultValue(22);
    public static final Option<String> SSH_USERNAME =
            Options.key("connection.ssh.username").stringType().noDefaultValue();
    public static final Option<String> SSH_AUTH_TYPE = Options.key("connection.ssh.auth-type")
            .singleChoice(String.class, java.util.List.of("PASSWORD", "PRIVATE_KEY"))
            .defaultValue("PASSWORD");
    public static final Option<String> SSH_PASSWORD =
            Options.key("connection.ssh.password").stringType().noDefaultValue();
    public static final Option<String> SSH_PRIVATE_KEY =
            Options.key("connection.ssh.private-key").stringType().noDefaultValue();
    public static final Option<String> SSH_PASSPHRASE =
            Options.key("connection.ssh.passphrase").stringType().noDefaultValue();
    public static final Option<Boolean> SSH_STRICT_HOST_KEYS =
            Options.key("connection.ssh.strict-host-keys").booleanType().defaultValue(false);
    public static final Option<String> SSH_KNOWN_HOSTS =
            Options.key("connection.ssh.known-hosts").stringType().noDefaultValue();
    /** Vendor-built loopback URL containing the literal {port} placeholder. */
    public static final Option<String> SSH_JDBC_URL =
            Options.key("connection.ssh.jdbc-url").stringType().noDefaultValue();

    public static final OptionRule RULE = OptionRule.builder()
            .required(URL, Conditions.matches(URL, "^jdbc:.+"))
            .optional(DRIVER_CLASS, DRIVER_ID, USERNAME, PASSWORD, HOST, PORT, DATABASE_TYPE, PROPERTIES)
            .optional(TIMEOUT, Conditions.greaterOrEqual(TIMEOUT, 1).and(Conditions.lessOrEqual(TIMEOUT, 2147483)))
            .optional(
                    SSH_ENABLED,
                    SSH_HOST,
                    SSH_PORT,
                    SSH_USERNAME,
                    SSH_AUTH_TYPE,
                    SSH_PASSWORD,
                    SSH_PRIVATE_KEY,
                    SSH_PASSPHRASE,
                    SSH_STRICT_HOST_KEYS,
                    SSH_KNOWN_HOSTS,
                    SSH_JDBC_URL)
            .conditionalRule(
                    SSH_ENABLED,
                    true,
                    OptionRule.builder()
                            .required(HOST, Conditions.notBlank(HOST))
                            .required(PORT, Conditions.greaterOrEqual(PORT, 1).and(Conditions.lessOrEqual(PORT, 65535)))
                            .required(SSH_HOST, Conditions.notBlank(SSH_HOST))
                            .required(
                                    SSH_PORT,
                                    Conditions.greaterOrEqual(SSH_PORT, 1).and(Conditions.lessOrEqual(SSH_PORT, 65535)))
                            .required(SSH_USERNAME, Conditions.notBlank(SSH_USERNAME))
                            .required(SSH_JDBC_URL, Conditions.matches(SSH_JDBC_URL, ".*127[.]0[.]0[.]1:\\{port\\}.*"))
                            .conditionalRule(
                                    SSH_AUTH_TYPE,
                                    "PASSWORD",
                                    OptionRule.builder()
                                            .required(SSH_PASSWORD, Conditions.notBlank(SSH_PASSWORD))
                                            .build())
                            .conditionalRule(
                                    SSH_AUTH_TYPE,
                                    "PRIVATE_KEY",
                                    OptionRule.builder()
                                            .required(SSH_PRIVATE_KEY, Conditions.notBlank(SSH_PRIVATE_KEY))
                                            .build())
                            .conditionalRule(
                                    SSH_STRICT_HOST_KEYS,
                                    true,
                                    OptionRule.builder()
                                            .required(SSH_KNOWN_HOSTS, Conditions.notBlank(SSH_KNOWN_HOSTS))
                                            .build())
                            .build())
            .build();

    private final ReadonlyConfig configuration;

    /** Creates connection options without opening a connection or loading a driver. */
    public JdbcConnectionOptions(ReadonlyConfig configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        ConfigValidator.of(configuration).validate(RULE);
    }

    public JdbcConnectionOptions(
            String url, String username, String password, String driverClass, Map<String, String> properties) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(URL.key(), url);
        values.put(USERNAME.key(), username);
        values.put(PASSWORD.key(), password);
        values.put(DRIVER_CLASS.key(), driverClass);
        values.put(PROPERTIES.key(), Map.copyOf(properties));
        this.configuration = ReadonlyConfig.fromMap(values);
        ConfigValidator.of(configuration).validate(RULE);
    }

    public JdbcConnectionOptions(String url, String username, String password) {
        this(url, username, password, null, Map.of());
    }

    public ReadonlyConfig configuration() {
        return configuration;
    }

    public String url() {
        return configuration.get(URL);
    }

    public String username() {
        return configuration.get(USERNAME);
    }

    public String password() {
        return configuration.get(PASSWORD);
    }

    /** Resolves a direct MySQL host from structured settings or a custom JDBC URL. */
    public String host() {
        String host = configuration.get(HOST);
        if (host != null) {
            return host;
        }
        if (url().startsWith("jdbc:mysql://")) {
            return URI.create(url().substring(5)).getHost();
        }
        return null;
    }

    /** Resolves the configured port, including the port of a custom MySQL URL. */
    public int port() {
        if (configuration.get(HOST) == null && url().startsWith("jdbc:mysql://")) {
            int port = URI.create(url().substring(5)).getPort();
            if (port > 0) {
                return port;
            }
        }
        Integer port = configuration.get(PORT);
        if (port != null && port > 0) {
            return port;
        }
        return 3306;
    }

    @Override
    public String toString() {
        return "JdbcConnectionOptions{credentials=redacted}";
    }
}
