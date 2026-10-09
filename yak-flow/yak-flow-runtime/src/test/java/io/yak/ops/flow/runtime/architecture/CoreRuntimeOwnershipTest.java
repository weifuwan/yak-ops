package io.yak.ops.flow.runtime.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.flow.runtime.execution.RuntimeTaskInfo;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.operators.OneInputOperator;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import org.junit.jupiter.api.Test;

class CoreRuntimeOwnershipTest {

    @Test
    void coreShouldOwnOnlyStableContractsAndGeneralTransformation() {
        assertEquals("io.yak.ops.core.api.common", TaskInfo.class.getPackageName());
        assertEquals("io.yak.ops.core.api.dag", Transformation.class.getPackageName());
        assertTrue(TaskInfo.class.isAssignableFrom(RuntimeTaskInfo.class));
    }

    @Test
    void runtimeShouldOwnStreamingGraphAndOperatorLifecycle() {
        assertEquals("io.yak.ops.flow.runtime.graph", StreamGraph.class.getPackageName());
        assertEquals("io.yak.ops.flow.runtime.graph", StreamGraphGenerator.class.getPackageName());
        assertEquals("io.yak.ops.flow.runtime.transformations", SourceTransformation.class.getPackageName());
        assertEquals("io.yak.ops.flow.runtime.operators", OneInputOperator.class.getPackageName());
    }

    @Test
    void runtimeTaskInfoShouldExposeActualValuesViaCoreContract() {
        TaskInfo info = new RuntimeTaskInfo(JobID.generate(), 7, 1, 3, 2);
        assertEquals(1, info.getIndexOfThisSubtask());
        assertEquals(3, info.getNumberOfParallelSubtasks());
        assertEquals(2, info.getAttemptNumber());
    }
}
