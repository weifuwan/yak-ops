package io.yak.ops.server.master.cluster.loadbalancer;

import io.yak.ops.server.master.cluster.IClusters;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeightedServer<T extends IClusters.IServerMetadata> {

    private T server;

    private double weight;

    private double currentWeight;

    public WeightedServer(T server, double weight) {
        this.server = server;
        this.weight = weight;
        this.currentWeight = 0; // Initialize currentWeight is 0
    }

}
