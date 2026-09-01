package io.yak.ops.common.bean.po.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_quality_monitor_revision")
public class QualityTaskRevisionPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private Long monitorId;
  private Integer revisionNo;
  private String monitorName;
  private String definitionJson;
  private String checksum;
  private LocalDateTime createdAt;
}
