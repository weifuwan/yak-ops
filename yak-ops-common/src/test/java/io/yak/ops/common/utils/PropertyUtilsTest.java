package io.yak.ops.common.utils;

import static com.google.common.truth.Truth.assertThat;

import io.yak.ops.common.constants.Constants;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Sets;

public class PropertyUtilsTest {

    @Test
    public void getString() {
        Assertions.assertNotNull(PropertyUtils.getString(Constants.FS_DEFAULT_FS));
    }

    @Test
    public void getSet() {
        Set<String> networkInterface = PropertyUtils.getSet("networkInterface", value -> {
            if (StringUtils.isEmpty(value)) {
                return Collections.emptySet();
            }
            return Arrays.stream(value.split(",")).map(String::trim).collect(Collectors.toSet());
        }, Sets.newHashSet("docker0"));
        Assertions.assertEquals(Sets.newHashSet("docker0"), networkInterface);
    }

    @Test
    void getByPrefix() {
        Map<String, String> awsProperties = PropertyUtils.getByPrefix("resource.aws.", "");
        assertThat(awsProperties).containsEntry("access.key.id", "minioadmin");
        assertThat(awsProperties).containsEntry("secret.access.key", "minioadmin");
        assertThat(awsProperties).containsEntry("region", "cn-north-1");
        assertThat(awsProperties).containsEntry("s3.bucket.name", "dolphinscheduler");
        assertThat(awsProperties).containsEntry("endpoint", "http://localhost:9000");
    }
}
