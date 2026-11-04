package io.yak.ops.api.validator;

import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.plugin.task.api.utils.GlobalParameterUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates global parameters: non-empty keys, no duplicates
 * <p> If globalParams is not valid, an {@link IllegalArgumentException} will be thrown. </p>
 */
@Slf4j
@Component
public class GlobalParamsValidator implements IValidator<String> {

    @Override
    public void validate(String globalParams) {
        if (StringUtils.isBlank(globalParams)) {
            return;
        }

        List<Property> params;
        try {
            params = GlobalParameterUtils.deserializeGlobalParameter(globalParams);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid globalParams", ex);
        }

        if (params == null || params.isEmpty()) {
            return;
        }

        Set<String> keys = new HashSet<>();
        for (Property p : params) {
            if (StringUtils.isBlank(p.getProp())) {
                throw new IllegalArgumentException("Global param key cannot be empty");
            }

            String key = p.getProp().trim();
            if (!keys.add(key)) {
                throw new IllegalArgumentException("Duplicate global param key: " + key);
            }
        }
    }
}
