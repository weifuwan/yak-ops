package io.yak.ops.api.dto.auditLog;

import io.yak.ops.common.enums.AuditModelType;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class AuditModelTypeDto {

    private String name;

    private List<AuditModelTypeDto> child = null;

    public static List<AuditModelTypeDto> getModelTypeDtoList() {
        List<AuditModelTypeDto> dtoList = new ArrayList<>();
        transFromEnumListToDto(dtoList, AuditModelType.getAuditModelTreeList());
        return dtoList;
    }

    public static List<AuditModelTypeDto> transFromEnumListToDto(List<AuditModelTypeDto> dtoList,
                                                                 List<AuditModelType> objectTypeList) {
        for (AuditModelType operationType : objectTypeList) {
            dtoList.add(transFromEnumToDto(operationType));
        }

        return dtoList;
    }

    public static AuditModelTypeDto transFromEnumToDto(AuditModelType operationType) {
        AuditModelTypeDto dto = new AuditModelTypeDto();
        dto.setName(operationType.getName());

        if (!operationType.getChild().isEmpty()) {
            dto.setChild(transFromEnumListToDto(new ArrayList<>(), operationType.getChild()));
        }

        return dto;
    }
}
