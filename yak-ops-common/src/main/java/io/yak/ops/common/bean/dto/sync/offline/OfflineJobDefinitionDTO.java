package io.yak.ops.common.bean.dto.sync.offline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 离线同步任务定义入参。
 *
 * <p>离线同步是固定的 Source -> Channel -> Sink 链路。字段映射、调度和通知策略作为
 * 任务级配置，与 basic、source、sink、channel 同级保存。</p>
 *
 * @author weifuwan
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class OfflineJobDefinitionDTO {

  private Long id;
  private OfflineJobBasicDTO basic;
  private OfflineJobEndpointDTO source;
  private OfflineJobEndpointDTO sink;
  private OfflineJobChannelDTO channel;

  /** 单表同步任务级字段映射。 */
  private OfflineJobMappingDTO mapping;

  /** 任务级 Cron、启停和失败重跑配置。 */
  private OfflineJobScheduleDTO schedule = new OfflineJobScheduleDTO();

  /** 任务级通知策略；缺省/null 保留历史 Project OWNER + IN_APP 默认行为。 */
  private OfflineJobNotificationDTO notification;

  /** 单表首次全量、后续按已提交上界增量的游标配置。 */
  private OfflineJobIncrementalDTO incremental;

  /** UI-only metadata such as the selected EmojiIconPicker icon. */
  private OfflineJobEditorMetaDTO editorMeta;
}
