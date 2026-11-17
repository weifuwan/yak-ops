package io.yak.ops.agent.service;

/**
 * Callback hooks emitted by the chat loop so the turn layer can stream tool activity.
 */
public interface AgentEventSink {

    AgentEventSink NOOP = new AgentEventSink() {

        @Override
        public void toolStarted(String tool, String arguments) {
        }

        @Override
        public void toolFinished(String tool, String resultSummary, long durationMs) {
        }
    };

    void toolStarted(String tool, String arguments);

    void toolFinished(String tool, String resultSummary, long durationMs);
}
