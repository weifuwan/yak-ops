package io.yak.ops.modeling.api.dto.dimension;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.yak.ops.modeling.api.dto.pagination.PaginationBaseDTO;
import io.yak.ops.dao.entity.enums.DimensionStoragePolicyEnum;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.modeling.domain.enums.DimensionTreeGroupingEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

/** Request model for dimension table modeling. */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DimensionTableDTO extends PaginationBaseDTO {
    private DwTypeEnum dwhType;
    private DimensionTreeGroupingEnum selectOption;
    private String martOrSubject;
    private String keyword;
    private List<String> busProcessIds;

    private String id;
    private String dwhId;
    private String timePeriodId;
    private String busTypeId;
    private String busProcessId;
    private String domainId;
    private String martId;
    private String subjectId;
    private String dimId;
    private String modifier;
    private String nameRuleId;
    private String tableName;
    private String cnName;
    private DimensionTableTypeEnum tableType;
    private DimensionStoragePolicyEnum storagePolicy;
    private Integer lifeCycle;
    private String director;
    private String remark;
    private Boolean submit;
    private Date createTime;
    private String createBy;
    private Date updateTime;
    private String updateBy;
}