package io.yak.ops.modeling.infrastructure.persistence.entity.asset;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.modeling.infrastructure.persistence.entity.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Persistence entities migrated from data_modal's standards and indicator domains. */
public final class ModelingAssetPOs {
    private ModelingAssetPOs() {
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_standard_directory")
    public static class StandardDirectoryPO extends BasePO {
        private String dirName;
        private String dirType;
        private String dirModule;
        private String parentId;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_field_standard")
    public static class FieldStandardPO extends BasePO {
        private String stdCode;
        private String enShortName;
        private String enName;
        private String cnName;
        private String fieldDataType;
        private Integer fieldLength;
        private Integer fieldPrecision;
        private Boolean fieldCanNull;
        private String fieldDefaultValue;
        private String businessDefinition;
        private String parentStd;
        private String parentId;
        private String reference;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_code_standard")
    public static class CodeStandardPO extends BasePO {
        private String parentId;
        private String enShortName;
        private String enName;
        private String cnName;
        private String remark;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_code_detail")
    public static class CodeDetailPO extends BasePO {
        private String codeId;
        private String enShortName;
        private String enName;
        private String cnName;
        private String remark;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_unit_standard")
    public static class UnitStandardPO extends BasePO {
        private String enShortName;
        private String enName;
        private String cnName;
        private String parentId;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_dictionary_standard")
    public static class DictionaryStandardPO extends BasePO {
        private String enShortName;
        private String enName;
        private String cnName;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_indicator_atom")
    public static class AtomIndicatorPO extends BasePO {
        private String enShortName;
        private String enName;
        private String cnName;
        private String director;
        private String remark;
        private String businessCaliber;
        private String busTypeId;
        private String domainId;
        private String busProcessId;
        private String calculateFunction;
        private Integer decimalNumber;
        private String unitId;
        private Boolean removeDuplicates;
        private Boolean submit;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_indicator_derivative")
    public static class DerivativeIndicatorPO extends BasePO {
        private String timePeriodId;
        private String modifierId;
        private String atomicId;
        private String dwhId;
        private String martId;
        private String subjectId;
        private String busTypeId;
        private String domainId;
        private String busProcessId;
        private String enShortName;
        private String enName;
        private String cnName;
        private String director;
        private String remark;
        private Boolean submit;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_indicator_compose")
    public static class ComposeIndicatorPO extends BasePO {
        private String dwhId;
        private String martId;
        private String subjectId;
        private String busTypeId;
        private String domainId;
        private String busProcessId;
        private String enShortName;
        private String enName;
        private String cnName;
        private String director;
        private String remark;
        private String computeMode;
        private String computeExpr;
        private String deriveId;
        private String compareType;
        private Boolean submit;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_indicator_modifier")
    public static class ModifierPO extends BasePO {
        private String modifierType;
        private String enShortName;
        private String enName;
        private String cnName;
        private String director;
        private String businessCaliber;
        private String remark;
        private Boolean submit;
        private String dwhId;
        private String martId;
        private String subjectId;
        private String busTypeId;
        private String domainId;
        private String dimensionTbId;
        private String dimensionFdId;
        private String codeId;
        private String codeDetailId;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_time_period_category")
    public static class TimePeriodCategoryPO extends BasePO {
        private String enName;
        private String cnName;
        private String parentId;
        private Boolean leaf;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_time_period")
    public static class TimePeriodPO extends BasePO {
        private String categoryId;
        private String enShortName;
        private String enName;
        private String cnName;
        private String director;
        private String remark;
        private Boolean submit;
    }

    @Data @EqualsAndHashCode(callSuper = true) @TableName("t_cockpit_version_management")
    public static class VersionManagementPO extends BasePO {
        private String sourceTableId;
        private String versionData;
        private String versionType;
        private Integer versionNumber;
    }
}
