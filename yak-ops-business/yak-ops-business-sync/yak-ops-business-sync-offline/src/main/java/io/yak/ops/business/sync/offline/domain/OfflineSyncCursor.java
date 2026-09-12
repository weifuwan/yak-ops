package io.yak.ops.business.sync.offline.domain;

/** Stable progress cursor owned by one OfflineSyncTask. */
public record OfflineSyncCursor(
    long taskId,
    String cursorId,
    String sourceColumn,
    String sourceSignature,
    String position,
    Long lastSucceededBatchId,
    long stateVersion) {

  public OfflineSyncCursor(
      long taskId,
      String cursorId,
      String sourceColumn,
      String position,
      Long lastSucceededBatchId,
      long stateVersion) {
    this(taskId, cursorId, sourceColumn, null, position, lastSucceededBatchId, stateVersion);
  }

  public OfflineSyncCursor {
    if (taskId <= 0L) throw new IllegalArgumentException("TaskId 必须大于 0");
    cursorId = requireText(cursorId, "cursorId 不能为空");
    sourceColumn = requireText(sourceColumn, "sourceColumn 不能为空");
    sourceSignature = normalize(sourceSignature);
    position = requireText(position, "position 不能为空");
    if (lastSucceededBatchId != null && lastSucceededBatchId <= 0L) {
      throw new IllegalArgumentException("lastSucceededBatchId 必须大于 0");
    }
    if (stateVersion < 1L) throw new IllegalArgumentException("stateVersion 必须大于 0");
  }

  private static String requireText(String value, String message) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
    return value.trim();
  }

  private static String normalize(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }
}
