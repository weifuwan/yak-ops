package io.yak.ops.common.bean.dto.sync.offline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 单表离线同步的 MAX 时间游标配置。 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class OfflineJobIncrementalDTO {

  private Boolean enabled = false;
  private String strategy = "MAX_TIMESTAMP";
  private String column;
  private String bootstrapMode = "SOURCE_CURRENT_MAX";
}
