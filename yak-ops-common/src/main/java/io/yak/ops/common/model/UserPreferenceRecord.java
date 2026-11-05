package io.yak.ops.common.model;

import io.yak.ops.common.enums.UserPreferenceScene;

import java.time.LocalDateTime;

import lombok.Data;

/** Public preference response, intentionally excluding the owning user ID. */
@Data
public class UserPreferenceRecord {

    private UserPreferenceScene scene;

    private String itemKey;

    private Boolean favorite;

    private Integer sortOrder;

    private Long useCount;

    private LocalDateTime lastUsedTime;
}
