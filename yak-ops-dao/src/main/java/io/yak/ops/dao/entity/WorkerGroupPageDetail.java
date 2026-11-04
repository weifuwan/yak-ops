package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.WorkerGroupSource;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class WorkerGroupPageDetail extends WorkerGroup {

    private WorkerGroupSource source;

    public WorkerGroupPageDetail(WorkerGroup workerGroup) {
        this.setId(workerGroup.getId());
        this.setName(workerGroup.getName());
        this.setAddrList(workerGroup.getAddrList());
        this.setCreateTime(workerGroup.getCreateTime());
        this.setUpdateTime(workerGroup.getUpdateTime());
        this.setDescription(workerGroup.getDescription());
        this.setSystemDefault(workerGroup.isSystemDefault());
    }
}
