package io.yak.ops.plugin.database.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.concurrent.TimeUnit;

/** Creates bounded MongoDB clients with Yak Ops control-plane timeouts. */
final class MongoClientFactory {

    private MongoClientFactory() {}

    static MongoClient create(MongoConnection connection, int timeoutSeconds) {
        int timeout = Math.max(1, timeoutSeconds);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connection.uri()))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(timeout, TimeUnit.SECONDS))
                .applyToSocketSettings(builder ->
                        builder.connectTimeout(timeout, TimeUnit.SECONDS).readTimeout(timeout, TimeUnit.SECONDS))
                .build();
        return MongoClients.create(settings);
    }
}
