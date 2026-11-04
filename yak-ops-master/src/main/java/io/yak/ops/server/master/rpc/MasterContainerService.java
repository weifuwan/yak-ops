package io.yak.ops.server.master.rpc;

import io.yak.ops.extract.master.IMasterContainerService;
import io.yak.ops.server.master.cluster.WorkerGroupChangeNotifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MasterContainerService implements IMasterContainerService {

    @Autowired
    private WorkerGroupChangeNotifier workerGroupChangeNotifier;

    @Override
    public void refreshWorkerGroup() {
        workerGroupChangeNotifier.detectWorkerGroupChanges();
    }
}
