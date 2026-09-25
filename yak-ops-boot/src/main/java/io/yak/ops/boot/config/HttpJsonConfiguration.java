package io.yak.ops.boot.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一 Yak Ops HTTP JSON 中 Java Time 类型的序列化与反序列化格式。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Configuration(proxyBeanMethods = false)
public class HttpJsonConfiguration {

    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm:ss";
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_PATTERN);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    @Bean
    Jackson2ObjectMapperBuilderCustomizer httpTimeJsonCustomizer() {
        return builder -> {
            builder.serializers(
                    new LocalDateSerializer(DATE_FORMATTER),
                    new LocalTimeSerializer(TIME_FORMATTER),
                    new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            builder.deserializers(
                    new LocalDateDeserializer(DATE_FORMATTER),
                    new LocalTimeDeserializer(TIME_FORMATTER),
                    new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
        };
    }
}
