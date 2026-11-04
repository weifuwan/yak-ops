package io.yak.ops.plugin.registry.etcd;

import io.yak.ops.registry.api.RegistryException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import io.etcd.jetcd.Client;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EtcdKeepAliveLeaseManager {

    private final Map<String, Long> keyLeaseCache = new ConcurrentHashMap<>();

    private final Client client;

    EtcdKeepAliveLeaseManager(Client client) {
        this.client = client;
    }

    long getOrCreateKeepAliveLease(String key, long timeToLive) {
        return keyLeaseCache.computeIfAbsent(key, $ -> {
            try {
                long leaseId = client.getLeaseClient().grant(timeToLive).get().getID();
                client.getLeaseClient().keepAlive(leaseId, new StreamObserver<LeaseKeepAliveResponse>() {

                    @Override
                    public void onNext(LeaseKeepAliveResponse value) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.error("Lease {} keep alive error, remove cache with key:{}", leaseId, key, t);
                        keyLeaseCache.remove(key);
                    }

                    @Override
                    public void onCompleted() {
                        log.error("Lease {} keep alive complete, remove cache with key:{}", leaseId, key);
                        keyLeaseCache.remove(key);
                    }
                });
                log.info("Lease {} keep alive create with key:{}", leaseId, key);
                return leaseId;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RegistryException("Failed to create lease key: " + key, e);
            } catch (ExecutionException e) {
                throw new RegistryException("Failed to create lease key: " + key, e);
            }
        });
    }

    Optional<Long> getKeepAliveLease(String key) {
        return Optional.ofNullable(keyLeaseCache.get(key));
    }
}
