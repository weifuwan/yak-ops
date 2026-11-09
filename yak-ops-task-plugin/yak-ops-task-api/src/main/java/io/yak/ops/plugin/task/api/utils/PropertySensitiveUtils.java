package io.yak.ops.plugin.task.api.utils;

import io.yak.ops.plugin.task.api.TaskConstants;
import io.yak.ops.plugin.task.api.model.Property;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PropertySensitiveUtils {

    public boolean isSensitive(Property property) {
        return property != null && property.isSensitive();
    }

    public boolean isSensitiveValuePlaceholder(String value) {
        return TaskConstants.SENSITIVE_DATA_MASK.equals(value);
    }

    public Property copy(Property property) {
        if (property == null) {
            return null;
        }
        return Property.builder()
                .prop(property.getProp())
                .direct(property.getDirect())
                .type(property.getType())
                .value(property.getValue())
                .sensitive(property.isSensitive())
                .build();
    }

    public List<Property> copy(List<Property> properties) {
        if (CollectionUtils.isEmpty(properties)) {
            return Collections.emptyList();
        }
        return properties.stream()
                .map(PropertySensitiveUtils::copy)
                .collect(Collectors.toList());
    }

    public Property maskSensitiveValue(Property property) {
        Property maskedProperty = copy(property);
        if (isSensitive(maskedProperty) && maskedProperty.getValue() != null) {
            maskedProperty.setValue(TaskConstants.SENSITIVE_DATA_MASK);
        }
        return maskedProperty;
    }

    public List<Property> maskSensitiveValues(List<Property> properties) {
        if (CollectionUtils.isEmpty(properties)) {
            return Collections.emptyList();
        }
        return properties.stream()
                .map(PropertySensitiveUtils::maskSensitiveValue)
                .collect(Collectors.toList());
    }

    public List<Property> mergeSensitiveValuePlaceholders(List<Property> submittedProperties,
                                                          List<Property> existingProperties) {
        if (CollectionUtils.isEmpty(submittedProperties)) {
            return Collections.emptyList();
        }
        Map<String, Property> existingPropertyMap = toPropMap(existingProperties);

        return submittedProperties.stream()
                .map(PropertySensitiveUtils::copy)
                .peek(property -> mergeSensitiveValuePlaceholder(property, existingPropertyMap))
                .collect(Collectors.toList());
    }

    public String findPlaceholderProp(List<Property> properties) {
        if (CollectionUtils.isEmpty(properties)) {
            return null;
        }
        for (Property property : properties) {
            if (property != null && isSensitiveValuePlaceholder(property.getValue())) {
                return property.getProp();
            }
        }
        return null;
    }

    public String findInvalidSensitivePlaceholderProp(List<Property> submittedProperties,
                                                      List<Property> existingProperties) {
        if (CollectionUtils.isEmpty(submittedProperties)) {
            return null;
        }
        Map<String, Property> existingPropertyMap = toPropMap(existingProperties);
        for (Property submitted : submittedProperties) {
            if (submitted == null || !isSensitiveValuePlaceholder(submitted.getValue())) {
                continue;
            }
            Property existing = existingPropertyMap.get(submitted.getProp());
            if (!isSensitive(submitted)) {
                if (existing != null && existing.isSensitive()) {
                    return submitted.getProp();
                }
                continue;
            }
            if (existing == null) {
                return submitted.getProp();
            }
        }
        return null;
    }

    private void mergeSensitiveValuePlaceholder(Property submittedProperty,
                                                 Map<String, Property> existingPropertyMap) {
        if (!isSensitive(submittedProperty) || !isSensitiveValuePlaceholder(submittedProperty.getValue())) {
            return;
        }
        Property existingProperty = existingPropertyMap.get(submittedProperty.getProp());
        if (existingProperty != null) {
            submittedProperty.setValue(existingProperty.getValue());
        }
    }

    private Map<String, Property> toPropMap(List<Property> properties) {
        return CollectionUtils.emptyIfNull(properties)
                .stream()
                .filter(Objects::nonNull)
                .filter(property -> property.getProp() != null)
                .collect(Collectors.toMap(Property::getProp, Function.identity(), (left, right) -> right));
    }
}
