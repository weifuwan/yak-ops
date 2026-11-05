package io.yak.ops.api.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;

/** The API never accepts a user ID; ownership comes from the current session. */
@Data
public class UserPreferenceFavoriteRequest {

    @NotNull
    private Boolean favorite;
}
