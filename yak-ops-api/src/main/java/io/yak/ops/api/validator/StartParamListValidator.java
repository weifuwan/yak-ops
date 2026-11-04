package io.yak.ops.api.validator;

import io.yak.ops.plugin.task.api.model.Property;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Validator for the list of start parameters (Property list).
 * <p> If startParamList is not valid, an {@link IllegalArgumentException} will be thrown. </p>
 */
@Slf4j
@Component
public class StartParamListValidator implements IValidator<List<Property>> {

    @Override
    public void validate(List<Property> startParamList) {
        if (CollectionUtils.isEmpty(startParamList)) {
            return;
        }

        Set<String> keys = new HashSet<>();
        for (Property param : startParamList) {
            if (StringUtils.isBlank(param.getProp())) {
                throw new IllegalArgumentException("Parameter key cannot be empty");
            }

            String key = param.getProp().trim();
            if (keys.contains(key)) {
                throw new IllegalArgumentException("Duplicate parameter key: " + key);
            }
            keys.add(key);
        }
    }
}
