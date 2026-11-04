package io.yak.ops.api.dto.resources;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResourceDto {

    private List<String> resourceAbsolutePaths;
}
