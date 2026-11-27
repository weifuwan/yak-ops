package io.yak.ops.plugin.task.datasync.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

class FlinkRestDataSyncRecoveryPointServiceTest {

    @Test
    void parsesLatestCompletedCheckpoint() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"latest\":{\"completed\":{"
                + "\"id\":12,\"status\":\"COMPLETED\","
                + "\"latest_ack_timestamp\":1730000000000,"
                + "\"external_path\":\"file:///checkpoints/chk-12\"}}}");
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        DataSyncExecutionHandle handle = DataSyncExecutionHandle.builder()
                .executionId("execution-1")
                .jobId("job-1")
                .restEndpoint("http://127.0.0.1:8081/")
                .build();

        DataSyncRecoveryPointSnapshot point =
                new FlinkRestDataSyncRecoveryPointService(client).latestCompletedCheckpoint(handle);

        assertEquals("execution-1:checkpoint:12", point.getRecoveryPointId());
        assertEquals("12", point.getCheckpointId());
        assertEquals("CHECKPOINT", point.getRecoveryType());
        assertEquals("file:///checkpoints/chk-12", point.getRestorePath());
        assertEquals(1730000000000L, point.getCompletedAt());
    }

    @Test
    void returnsNullWhenNoCompletedCheckpointExists() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"latest\":{\"completed\":null}}");
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        DataSyncExecutionHandle handle = DataSyncExecutionHandle.builder()
                .executionId("execution-1")
                .jobId("job-1")
                .restEndpoint("http://127.0.0.1:8081")
                .build();

        assertNull(new FlinkRestDataSyncRecoveryPointService(client).latestCompletedCheckpoint(handle));
    }

    @Test
    void ignoresSavepointsReturnedByTheCheckpointEndpoint() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"latest\":{\"completed\":{"
                + "\"id\":13,\"status\":\"COMPLETED\",\"is_savepoint\":true,"
                + "\"latest_ack_timestamp\":1730000000000,"
                + "\"external_path\":\"file:///savepoints/savepoint-13\"}}}");
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        DataSyncExecutionHandle handle = DataSyncExecutionHandle.builder()
                .executionId("execution-1")
                .jobId("job-1")
                .restEndpoint("http://127.0.0.1:8081")
                .build();

        assertNull(new FlinkRestDataSyncRecoveryPointService(client).latestCompletedCheckpoint(handle));
    }

    @Test
    void rejectsNonSuccessfulFlinkRestResponses() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("unavailable");
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);

        DataSyncExecutionHandle handle = DataSyncExecutionHandle.builder()
                .executionId("execution-1")
                .jobId("job-1")
                .restEndpoint("http://127.0.0.1:8081")
                .build();

        assertThrows(IOException.class,
                () -> new FlinkRestDataSyncRecoveryPointService(client).latestCompletedCheckpoint(handle));
    }
}
