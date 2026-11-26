package io.yak.ops.extract.master;

import io.yak.ops.extract.base.RpcMethod;
import io.yak.ops.extract.base.RpcService;
import io.yak.ops.extract.master.transportor.datasync.DataSyncTaskLifecycleEventRequest;
import io.yak.ops.extract.master.transportor.datasync.DataSyncTaskLifecycleEventResponse;

@RpcService
public interface IDataSyncTaskLifecycleOperator {

    @RpcMethod
    DataSyncTaskLifecycleEventResponse reportLifecycleEvent(DataSyncTaskLifecycleEventRequest request);
}
