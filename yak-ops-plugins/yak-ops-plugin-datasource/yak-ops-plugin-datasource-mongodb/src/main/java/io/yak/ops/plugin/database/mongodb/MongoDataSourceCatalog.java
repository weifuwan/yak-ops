package io.yak.ops.plugin.database.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import io.yak.ops.spi.datasource.DataSourceCatalog;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogReadRequest;
import io.yak.ops.spi.datasource.catalog.DataSourceTablePath;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import io.yak.ops.spi.datasource.query.DataSourceQueryColumn;
import io.yak.ops.spi.datasource.query.DataSourceQueryResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bson.BsonDocument;

/** Maps MongoDB databases, collections and sampled fields to the Yak Ops Catalog contract. */
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

    @Override
    public List<DataSourceColumn> describe(DataSourceCatalogReadRequest request) {
        if (request == null || request.sqlMode()) {
            throw catalog("MongoDB Catalog 不支持 SQL describe，请选择 Collection");
        }
        Path path = resolvePath(request.tablePath());
        return discover(path.database(), path.collection());
    }

    @Override
    public DataSourceQueryResult preview(DataSourceCatalogReadRequest request, int limit) {
        if (request == null || request.sqlMode()) {
            throw catalog("MongoDB 数据预览不支持 SQL，请选择 Collection");
        }
        Path path = resolvePath(request.tablePath());
        int safeLimit = Math.max(1, Math.min(200, limit));

        try (MongoClient client = client()) {
            MongoCollection<BsonDocument> collection = collection(client, path);
            List<BsonDocument> documents = new ArrayList<>();
            try (MongoCursor<BsonDocument> cursor =
                    collection.find().limit(safeLimit).iterator()) {
                while (cursor.hasNext()) documents.add(cursor.next());
            }

            List<DataSourceColumn> fields = documents.isEmpty()
                    ? discover(path.database(), path.collection())
                    : MongoSchemaDiscovery.discover(documents);
            List<DataSourceQueryColumn> columns = fields.stream()
                    .map(field -> new DataSourceQueryColumn(field.getName(), field.getName(), field.getName(), true))
                    .toList();

            List<Map<String, Object>> rows = new ArrayList<>();
            for (BsonDocument document : documents) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (DataSourceColumn field : fields) {
                    row.put(field.getName(), MongoSchemaDiscovery.previewValue(document, field.getName()));
                }
                rows.add(row);
            }
            long total = collection.estimatedDocumentCount();
            return new DataSourceQueryResult(columns, rows, total);
        } catch (RuntimeException failure) {
            throw catalog("预览 MongoDB Collection 失败：" + path, failure);
        }
    }

    @Override
    public long count(DataSourceCatalogReadRequest request) {
        if (request == null || request.sqlMode()) {
            throw catalog("MongoDB count 不支持 SQL，请选择 Collection");
        }
        Path path = resolvePath(request.tablePath());
        try (MongoClient client = client()) {
            return collection(client, path).estimatedDocumentCount();
        } catch (RuntimeException failure) {
            throw catalog("统计 MongoDB Collection 失败：" + path, failure);
        }
    }

    @Override
    public String buildSqlTemplate(String tablePath) {
        throw catalog("MongoDB 不提供 SQL 模板能力");
    }

    @Override
    public String resolveSql(String sql, DataSourceCatalogReadRequest request) {
        throw catalog("MongoDB 不提供 SQL 变量解析能力");
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

    private MongoCollection<BsonDocument> collection(MongoClient client, Path path) {
        MongoDatabase database = client.getDatabase(path.database());
        return database.getCollection(path.collection(), BsonDocument.class);
    }

    private MongoClient client() {
        return MongoClientFactory.create(connection, timeoutSeconds);
    }

    private Path resolvePath(String value) {
        String path = requireCollection(value);
        String defaultDatabase = connection.database();
        String prefix = defaultDatabase + ".";
        if (path.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return new Path(defaultDatabase, requireCollection(path.substring(prefix.length())));
        }
        // MongoDB collection names may legally contain dots. Treat an unqualified catalog-read path as
        // a collection in the datasource default database instead of guessing database.collection.
        return new Path(defaultDatabase, path);
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

    private record Path(String database, String collection) {
        @Override
        public String toString() {
            return database + "." + collection;
        }
    }
}
