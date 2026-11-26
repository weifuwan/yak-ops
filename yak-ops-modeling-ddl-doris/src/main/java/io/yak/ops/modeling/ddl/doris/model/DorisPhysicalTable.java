package io.yak.ops.modeling.ddl.doris.model;

import io.yak.ops.modeling.ddl.model.DdlColumnMetadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Live Doris table definition captured from SHOW CREATE TABLE and catalog metadata. */
public class DorisPhysicalTable {

    private final String createDdl;
    private final DorisTableModel tableModel;
    private final List<String> keyFields;
    private final Map<String, DdlColumnMetadata> columns;
    private final String partitionClause;
    private final String distributionClause;
    private final Map<String, String> properties;
    private final Set<String> protectedColumns;

    public DorisPhysicalTable(
            String createDdl,
            DorisTableModel tableModel,
            List<String> keyFields,
            Map<String, DdlColumnMetadata> columns,
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
                ? Collections.<String, DdlColumnMetadata>emptyMap()
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
    public DorisTableModel getTableModel() { return tableModel; }
    public List<String> getKeyFields() { return keyFields; }
    public Map<String, DdlColumnMetadata> getColumns() { return columns; }
    public String getPartitionClause() { return partitionClause; }
    public String getDistributionClause() { return distributionClause; }
    public Map<String, String> getProperties() { return properties; }
    public Set<String> getProtectedColumns() { return protectedColumns; }
}
