package org.apache.flink.cdc.composer;

/** A pipeline execution that can be executed by a computing engine. */
public interface PipelineExecution {

    /** Execute the pipeline. */
    ExecutionInfo execute() throws Exception;

    /** Information of the execution. */
    class ExecutionInfo {
        private final String id;
        private final String description;

        public ExecutionInfo(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }
    }
}
