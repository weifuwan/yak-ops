package io.yak.ops.plugin.task.api.utils;

import io.yak.ops.common.utils.PropertyUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ShellUtils {

    public List<String> ENV_SOURCE_LIST = Arrays.stream(
            Optional.ofNullable(PropertyUtils.getString("shell.env_source_list")).map(s -> s.split(","))
                    .orElse(new String[0]))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
}
