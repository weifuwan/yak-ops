package io.yak.ops.common.bean.po.sync.offline;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 离线同步 Task Cursor 持久化模型。 */
@Data
@TableName("yak_offline_sync_cursor")
public class OfflineSyncCursorPO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long jobDefinitionId;
  private String cursorId;
  private String sourceColumn;
  private String sourceSignature;
  private String positionValue;
  private Long lastSucceededBatchId;
  private Long stateVersion;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
