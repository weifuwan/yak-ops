package io.yak.ops.plugin.database.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCursor;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceTablePath;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bson.BsonDocument;

/**
 * 将 MongoDB Database、Collection 和采样字段映射为统一 Catalog 元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
final class MongoDataSourceCatalog implements DataSourceCatalog {

    private final MongoConnection connection;
    private final int timeoutSeconds;

    MongoDataSourceCatalog(MongoConnection connection, int timeoutSeconds) {
        this.connection = connection;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    @Override
    public List<String> listDatabases() {
        try (MongoClient client = client()) {
            List<String> result = new ArrayList<>();
            for (String database : client.listDatabaseNames()) result.add(database);
            result.sort(String.CASE_INSENSITIVE_ORDER);
            return List.copyOf(result);
        } catch (RuntimeException failure) {
            throw catalog("读取 MongoDB database 列表失败", failure);
        }
    }

    @Override
    public List<String> listSchemas(String database) {
        requireDatabase(database);
        return List.of();
    }

    @Override
    public List<DataSourceTable> listTables(DataSourceCatalogQuery query) {
        String database = requireDatabase(query == null ? null : query.getDatabase());
        requireNoSchema(query == null ? null : query.getSchema());
        String keyword = normalize(query == null ? null : query.getKeyword());
        Integer limit = query == null ? null : query.getLimit();

        try (MongoClient client = client()) {
            List<DataSourceTable> result = new ArrayList<>();
            for (String collection : client.getDatabase(database).listCollectionNames()) {
                if (keyword != null
                        && !collection.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                result.add(new DataSourceTable(database, null, collection, "COLLECTION", null));
            }
            result.sort(Comparator.comparing(DataSourceTable::getName, String.CASE_INSENSITIVE_ORDER));
            if (limit != null && limit > 0 && result.size() > limit) {
                return List.copyOf(result.subList(0, limit));
            }
            return List.copyOf(result);
        } catch (RuntimeException failure) {
            throw catalog("读取 MongoDB collection 列表失败：database=" + database, failure);
        }
    }

    @Override
    public List<DataSourceColumn> listColumns(DataSourceTablePath tablePath) {
        if (tablePath == null) throw catalog("MongoDB tablePath 不能为空");
        requireNoSchema(tablePath.getSchema());
        String database = requireDatabase(tablePath.getDatabase());
        String collection = requireCollection(tablePath.getTable());
        return discover(database, collection);
    }

    private List<DataSourceColumn> discover(String database, String collection) {
        try (MongoClient client = client()) {
            List<BsonDocument> samples = new ArrayList<>();
            try (MongoCursor<BsonDocument> cursor = client.getDatabase(database)
                    .getCollection(collection, BsonDocument.class)
                    .find()
                    .limit(MongoSchemaDiscovery.SAMPLE_SIZE)
                    .iterator()) {
                while (cursor.hasNext()) samples.add(cursor.next());
            }
            return MongoSchemaDiscovery.discover(samples);
        } catch (RuntimeException failure) {
            throw catalog("发现 MongoDB Collection 字段失败：" + database + "." + collection, failure);
        }
    }

    private MongoClient client() {
        return MongoClientFactory.create(connection, timeoutSeconds);
    }

    private String requireDatabase(String value) {
        String normalized = normalize(value);
        return normalized == null ? connection.database() : normalized;
    }

    private String requireCollection(String value) {
        String normalized = normalize(value);
        if (normalized == null) throw catalog("MongoDB Collection 不能为空");
        return normalized;
    }

    private void requireNoSchema(String schema) {
        if (normalize(schema) != null) {
            throw catalog("MongoDB 没有独立 Schema 层级，请使用 Database + Collection");
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private DataSourcePluginException catalog(String message) {
        return new DataSourcePluginException(Operation.CATALOG, message);
    }

    private DataSourcePluginException catalog(String message, Throwable cause) {
        return new DataSourcePluginException(Operation.CATALOG, message, cause);
    }
}
