package io.yak.ops.agent.event;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

/**
 * In-process SSE fan-out per turn. Events carry a monotonically increasing id so a
 * reconnect can be reconciled against the persisted turn state.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class AgentEventBroker {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String turnId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.computeIfAbsent(turnId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(turnId, emitter));
        emitter.onTimeout(() -> remove(turnId, emitter));
        emitter.onError(throwable -> remove(turnId, emitter));
        return emitter;
    }

    public void publish(String turnId, String type, Map<String, Object> payload) {
        long seq = sequences.computeIfAbsent(turnId, key -> new AtomicLong()).incrementAndGet();
        AgentEvent event = AgentEvent.builder()
                .seq(seq)
                .type(type)
                .turnId(turnId)
                .payload(payload)
                .build();
        CopyOnWriteArrayList<SseEmitter> targets = emitters.get(turnId);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(seq))
                        .name(type)
                        .data(event.getPayload()));
            } catch (IOException ex) {
                log.debug("Dropping closed SSE emitter for turn {}", turnId);
                remove(turnId, emitter);
            }
        }
    }

    public void complete(String turnId) {
        CopyOnWriteArrayList<SseEmitter> targets = emitters.remove(turnId);
        if (targets == null) {
            return;
        }
        for (SseEmitter emitter : targets) {
            try {
                emitter.complete();
            } catch (Exception ex) {
                log.debug("Emitter already closed for turn {}", turnId);
            }
        }
        sequences.remove(turnId);
    }

    private void remove(String turnId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> targets = emitters.get(turnId);
        if (targets != null) {
            targets.remove(emitter);
            if (targets.isEmpty()) {
                emitters.remove(turnId);
            }
        }
    }
}
