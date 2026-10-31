package io.yak.ops.common.bean.dto.task;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import io.yak.ops.common.enums.task.DefinitionStatus;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Task page request with exact indexed parameter filters and an optional Data Sync product filter. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DefinitionQueryDTO extends PageQueryDTO {
    /** Optional search term for the definition name. */
    @Size(max = 256)
    private String keyword;

    /** Optional registered plugin type filter. */
    private String taskType;
    /** Optional publication status filter, independent of runtime state. */
    private DefinitionStatus status;
    /**
     * Exact filters on server-generated parameter indexes. For {@code taskType=DATA_SYNC},
     * {@code dataSyncProfile} also accepts {@code SINGLE_TABLE_BATCH} or {@code DATABASE_REPLICATION};
     * it filters the product view derived from the definition without changing its execution mode.
     */
    private Map<String, String> parameterFilters;
}
