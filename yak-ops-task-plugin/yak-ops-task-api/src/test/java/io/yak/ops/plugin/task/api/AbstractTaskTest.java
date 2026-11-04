package io.yak.ops.plugin.task.api;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.parameters.SubWorkflowParameters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AbstractTaskTest {

    @Test
    public void testFindFlinkJobId() {
        String jobId = "cca7bc1061d61cf15238e92312c2fc20";
        Pattern FLINK_APPLICATION_REGEX = Pattern.compile(TaskConstants.FLINK_APPLICATION_REGEX);
        Matcher matcher = FLINK_APPLICATION_REGEX.matcher("Job has been submitted with JobID " + jobId);
        String str = null;
        if (matcher.find()) {
            str = matcher.group();
        }
        Assertions.assertNotNull(str);
        Assertions.assertEquals(jobId, str.substring(6));
    }

    @Test
    public void testGetExitStatus() {
        AbstractTask task = new TestTask();

        task.setExitStatusCode(TaskConstants.EXIT_CODE_SUCCESS);
        Assertions.assertEquals(TaskExecutionStatus.SUCCESS, task.getExitStatus());

        task.setExitStatusCode(TaskConstants.EXIT_CODE_KILL);
        Assertions.assertEquals(TaskExecutionStatus.KILL, task.getExitStatus());

        task.setExitStatusCode(TaskConstants.EXIT_CODE_HARD_KILL);
        Assertions.assertEquals(TaskExecutionStatus.KILL, task.getExitStatus());

        task.setExitStatusCode(TaskConstants.EXIT_CODE_SIGINT_KILL);
        Assertions.assertEquals(TaskExecutionStatus.KILL, task.getExitStatus());

        task.setExitStatusCode(TaskConstants.EXIT_CODE_FAILURE);
        Assertions.assertEquals(TaskExecutionStatus.FAILURE, task.getExitStatus());
    }

    private static final class TestTask extends AbstractTask {

        private TestTask() {
            super(new TaskExecutionContext());
        }

        @Override
        public void handle(TaskCallBack taskCallBack) {
        }

        @Override
        public void cancel() {
        }

        @Override
        public AbstractParameters getParameters() {
            return new SubWorkflowParameters();
        }
    }

}
