package io.yak.ops.dao.plugin.api;

import java.util.Arrays;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class DatabaseEnvironmentCondition implements Condition {

    private final String profile;

    public DatabaseEnvironmentCondition(String profile) {
        this.profile = profile;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String[] activeProfiles = context.getEnvironment().getActiveProfiles();
        return Arrays.asList(activeProfiles).contains(profile);
    }
}
