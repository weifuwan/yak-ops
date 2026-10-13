package io.yak.ops.boot.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Masks credential-bearing fields in formatted events, including throwable stacks. */
public final class SensitiveDataConverter extends ClassicConverter {

    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(\\b(?:password|passwd|pwd|token|secret|authorization|api[_-]?key|access[_-]?key|secret[_-]?key)\\b[\\\"']?\\s*[:=]\\s*[\\\"']?)([^\\\"'\\s&,;}]+)");
    private static final Pattern USER_INFO = Pattern.compile("(?i)(\\b(?:jdbc:)?[a-z]+://[^:/@\\s]+:)([^@/\\s]+)(@)");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) {
            message = "";
        }
        if (event.getThrowableProxy() != null) {
            message += System.lineSeparator() + ThrowableProxyUtil.asString(event.getThrowableProxy());
        }
        return mask(message);
    }

    /**
     * @param content untrusted diagnostic text
     * @return text with recognized credential values replaced
     */
    public static String mask(String content) {
        return replace(KEY_VALUE, replace(USER_INFO, content));
    }

    private static String replace(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + "******";
            if (matcher.groupCount() == 3) {
                replacement += matcher.group(3);
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
