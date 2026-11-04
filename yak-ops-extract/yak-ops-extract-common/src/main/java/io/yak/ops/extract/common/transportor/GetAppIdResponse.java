package io.yak.ops.extract.common.transportor;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetAppIdResponse {

    private List<String> appIds;

}
