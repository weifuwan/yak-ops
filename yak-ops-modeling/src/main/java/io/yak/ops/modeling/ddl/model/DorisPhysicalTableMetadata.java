package io.yak.ops.modeling.ddl.model;

import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Live physical Doris table definition captured from SHOW CREATE TABLE and column metadata. */
public class DorisPhysicalTableMetadata {

    private final String createDdl;
    private final DorisDataModelEnum tableModel;
    private final List<String> keyFields;
    private final Map<String, DorisColumnMetadata> columns;
    private final String partitionClause;
    private final String distributionClause;
    private final Map<String, String> properties;
    private final Set<String> protectedColumns;

    public DorisPhysicalTableMetadata(
            String createDdl,
            DorisDataModelEnum tableModel,
            List<String> keyFields,
            Map<String, DorisColumnMetadata> columns,
            String partitionClause,
            String distributionClause,
            Map<String, String> properties,
            Set<String> protectedColumns) {
        this.createDdl = createDdl;
        this.tableModel = tableModel;
        this.keyFields = keyFields == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(keyFields));
        this.columns = columns == null
                ? Collections.<String, DorisColumnMetadata>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(columns));
        this.partitionClause = partitionClause;
        this.distributionClause = distributionClause;
        this.properties = properties == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        this.protectedColumns = protectedColumns == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new java.util.LinkedHashSet<>(protectedColumns));
    }

    public String getCreateDdl() { return createDdl; }
    public DorisDataModelEnum getTableModel() { return tableModel; }
    public List<String> getKeyFields() { return keyFields; }
    public Map<String, DorisColumnMetadata> getColumns() { return columns; }
    public String getPartitionClause() { return partitionClause; }
    public String getDistributionClause() { return distributionClause; }
    public Map<String, String> getProperties() { return properties; }
    public Set<String> getProtectedColumns() { return protectedColumns; }
}
