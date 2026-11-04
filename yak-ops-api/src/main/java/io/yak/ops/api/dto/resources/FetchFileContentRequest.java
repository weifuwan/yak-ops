package io.yak.ops.api.dto.resources;

import io.yak.ops.dao.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FetchFileContentRequest {

    private User loginUser;
    private String resourceFileAbsolutePath;
    private int skipLineNum;
    private int limit;
}
