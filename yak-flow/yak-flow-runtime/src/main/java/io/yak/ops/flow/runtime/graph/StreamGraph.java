package io.yak.ops.flow.runtime.graph;

import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.dag.Pipeline;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 批流统一的内存执行拓扑，实现 Pipeline 提交契约。
 *
 * <p>节点与边在构造时完成结构校验并冻结；不包含 JobID、运行状态、线程或连接。
 * StreamGraph 可以作为多个独立 Job 的共同执行定义。
 *
 * <p>这里只支持 Source、单输入 Operator 和 Sink。边已携带分区策略，
 * 但双输入、Union、Side Output 等能力仍不属于当前执行契约。
 *
 * @author weifuwan
 */
public final class StreamGraph implements Pipeline {

    private final Map<Integer, StreamNode> nodes;
    private final List<StreamEdge> edges;
    private final Map<Integer, List<StreamEdge>> incoming;
    private final Map<Integer, List<StreamEdge>> outgoing;
    private final List<StreamNode> sourceNodes;
    private final List<StreamNode> sinkNodes;
    private final List<StreamNode> topologicalNodes;

    public StreamGraph(Collection<StreamNode> streamNodes, Collection<StreamEdge> streamEdges) {
        Objects.requireNonNull(streamNodes, "streamNodes 不能为空");
        Objects.requireNonNull(streamEdges, "streamEdges 不能为空");

        Map<Integer, StreamNode> byId = new LinkedHashMap<>();
        Set<String> uids = new HashSet<>();
        for (StreamNode node : streamNodes) {
            Objects.requireNonNull(node, "StreamNode 不能为空");
            if (byId.putIfAbsent(node.getId(), node) != null) {
                throw new IllegalArgumentException("StreamNode ID 重复：" + node.getId());
            }
            if (node.getUid() != null && !uids.add(node.getUid())) {
                throw new IllegalArgumentException("稳定算子 UID 重复：" + node.getUid());
            }
        }
        if (byId.isEmpty()) {
            throw new IllegalArgumentException("StreamGraph 至少需要一个节点");
        }

        Map<Integer, List<StreamEdge>> in = new LinkedHashMap<>();
        Map<Integer, List<StreamEdge>> out = new LinkedHashMap<>();
        byId.keySet().forEach(id -> {
            in.put(id, new ArrayList<>());
            out.put(id, new ArrayList<>());
        });

        Set<Long> seen = new HashSet<>();
        List<StreamEdge> checkedEdges = new ArrayList<>();
        for (StreamEdge edge : streamEdges) {
            Objects.requireNonNull(edge, "StreamEdge 不能为空");
            StreamNode source = byId.get(edge.sourceId());
            StreamNode target = byId.get(edge.targetId());
            if (source == null || target == null) {
                throw new IllegalArgumentException("StreamEdge 引用了不存在的节点：" + edge);
            }
            long edgeIdentity = ((long) edge.sourceId() << 32) | (edge.targetId() & 0xffffffffL);
            if (!seen.add(edgeIdentity)) {
                throw new IllegalArgumentException("相同 Source/Target 不允许重复连接：" + edge);
            }
            if (edge.partitioning() == StreamPartitioning.FORWARD
                    && source.getParallelism() != target.getParallelism()) {
                throw new IllegalArgumentException("FORWARD 要求上下游并行度相同：" + edge);
            }
            if (source.isSink() || target.isSource()) {
                throw new IllegalArgumentException("不允许从 Sink 输出或向 Source 输入：" + edge);
            }
            Class<?> expectedInput = target.getInputType().orElseThrow();
            if (!expectedInput.isAssignableFrom(source.getOutputType())) {
                throw new IllegalArgumentException("数据类型不兼容："
                        + source.getOutputType().getName() + " -> " + expectedInput.getName() + "，边=" + edge);
            }
            checkedEdges.add(edge);
            out.get(edge.sourceId()).add(edge);
            in.get(edge.targetId()).add(edge);
        }

        List<StreamNode> sources = new ArrayList<>();
        List<StreamNode> sinks = new ArrayList<>();
        for (StreamNode node : byId.values()) {
            int inDegree = in.get(node.getId()).size();
            int outDegree = out.get(node.getId()).size();
            if (node.isSource()) {
                if (inDegree != 0 || outDegree == 0) {
                    throw new IllegalArgumentException("Source 必须没有输入且至少有一个输出：" + node.getId());
                }
                sources.add(node);
            } else if (node.isOperator()) {
                if (inDegree != 1 || outDegree == 0) {
                    throw new IllegalArgumentException("单输入 Operator 必须有一个输入且至少有一个输出：" + node.getId());
                }
            } else if (node.isSink()) {
                if (inDegree != 1 || outDegree != 0) {
                    throw new IllegalArgumentException("Sink 必须有一个输入且没有输出：" + node.getId());
                }
                sinks.add(node);
            } else {
                throw new IllegalArgumentException("不支持的节点类型：" + node.getId());
            }
        }
        if (sources.isEmpty() || sinks.isEmpty()) {
            throw new IllegalArgumentException("StreamGraph 至少需要一个 Source 和一个 Sink");
        }

        this.topologicalNodes = List.copyOf(topologicalSort(byId, in, out));
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
        this.edges = List.copyOf(checkedEdges);
        this.incoming = freezeEdges(in);
        this.outgoing = freezeEdges(out);
        this.sourceNodes = List.copyOf(sources);
        this.sinkNodes = List.copyOf(sinks);
    }

    private static List<StreamNode> topologicalSort(
            Map<Integer, StreamNode> byId,
            Map<Integer, List<StreamEdge>> incoming,
            Map<Integer, List<StreamEdge>> outgoing) {
        Map<Integer, Integer> remaining = new LinkedHashMap<>();
        Deque<Integer> ready = new ArrayDeque<>();
        for (Integer id : byId.keySet()) {
            int degree = incoming.get(id).size();
            remaining.put(id, degree);
            if (degree == 0) {
                ready.addLast(id);
            }
        }

        List<StreamNode> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            int id = ready.removeFirst();
            order.add(byId.get(id));
            for (StreamEdge edge : outgoing.get(id)) {
                int degree = remaining.merge(edge.targetId(), -1, Integer::sum);
                if (degree == 0) {
                    ready.addLast(edge.targetId());
                }
            }
        }
        if (order.size() != byId.size()) {
            throw new IllegalArgumentException("StreamGraph 存在环路或不可拓扑排序的节点");
        }
        return order;
    }

    private static Map<Integer, List<StreamEdge>> freezeEdges(Map<Integer, List<StreamEdge>> values) {
        Map<Integer, List<StreamEdge>> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }

    /** 根据节点 ID 查询执行图节点；节点不存在时返回 null。 */
    public StreamNode getStreamNode(int id) {
        return nodes.get(id);
    }

    public Collection<StreamNode> getStreamNodes() {
        return nodes.values();
    }

    public List<StreamEdge> getStreamEdges() {
        return edges;
    }

    public List<StreamEdge> getInEdges(int nodeId) {
        return requireEdges(incoming, nodeId);
    }

    public List<StreamEdge> getOutEdges(int nodeId) {
        return requireEdges(outgoing, nodeId);
    }

    private static List<StreamEdge> requireEdges(Map<Integer, List<StreamEdge>> index, int nodeId) {
        List<StreamEdge> result = index.get(nodeId);
        if (result == null) {
            throw new IllegalArgumentException("StreamNode 不存在：" + nodeId);
        }
        return result;
    }

    public List<StreamNode> getSourceNodes() {
        return sourceNodes;
    }

    public List<StreamNode> getSinkNodes() {
        return sinkNodes;
    }

    /** 返回从上游到下游的拓扑顺序，便于后续执行图编译。 */
    public List<StreamNode> getTopologicalNodes() {
        return topologicalNodes;
    }

    /** 只有所有 Source 均有界，整张图才视为有界。 */
    public boolean isBounded() {
        return sourceNodes.stream().allMatch(node -> node.getBoundedness().orElseThrow() == Boundedness.BOUNDED);
    }
}
