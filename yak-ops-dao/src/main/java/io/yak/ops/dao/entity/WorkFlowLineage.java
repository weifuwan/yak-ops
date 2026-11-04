package io.yak.ops.dao.entity;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WorkFlowLineage {

    private List<WorkFlowRelation> workFlowRelationList;
    private List<WorkFlowRelationDetail> workFlowRelationDetailList;
}
