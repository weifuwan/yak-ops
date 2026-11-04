package io.yak.ops.api.dto.resources;

import io.yak.ops.dao.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeleteResourceRequest {

    private User loginUser;
    private String resourceAbsolutePath;
}
