package io.yak.ops.dao.entity;

import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkFlowRelation {

    private long sourceWorkFlowCode;
    private long targetWorkFlowCode;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WorkFlowRelation that = (WorkFlowRelation) o;
        return sourceWorkFlowCode == that.sourceWorkFlowCode
                && targetWorkFlowCode == that.targetWorkFlowCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceWorkFlowCode, targetWorkFlowCode);
    }
}
