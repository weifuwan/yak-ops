package io.yak.ops.api.dto.auditLog;

import io.yak.ops.common.enums.AuditOperationType;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class AuditOperationTypeDto {

    private String name;

    public static List<AuditOperationTypeDto> getOperationTypeDtoList() {
        List<AuditOperationTypeDto> dtoList = new ArrayList<>();
        for (AuditOperationType operationType : AuditOperationType.getOperationList()) {
            AuditOperationTypeDto dto = new AuditOperationTypeDto();
            dto.setName(operationType.getName());
            dtoList.add(dto);
        }

        return dtoList;
    }
}
