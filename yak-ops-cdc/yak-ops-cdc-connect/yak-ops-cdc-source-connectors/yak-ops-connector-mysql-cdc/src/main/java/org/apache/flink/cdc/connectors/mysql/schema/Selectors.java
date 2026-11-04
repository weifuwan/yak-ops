package org.apache.flink.cdc.connectors.mysql.schema;

import org.apache.flink.cdc.common.utils.Predicates;

import io.debezium.relational.TableId;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Selectors for filtering tables. */
public class Selectors {

    private List<Selector> selectors;

    private Selectors() {}

    /**
     * A {@link Selector} that determines whether a table identified by a given {@link TableId} is
     * to be included.
     */
    private static class Selector {
        private final Predicate<String> namespacePred;
        private final Predicate<String> tableNamePred;

        public Selector(String namespace, String tableName) {
            this.namespacePred =
                    namespace == null ? (namespacePred) -> false : Predicates.includes(namespace);
            this.tableNamePred =
                    tableName == null ? (tableNamePred) -> false : Predicates.includes(tableName);
        }

        public boolean isMatch(TableId tableId) {

            String namespace = tableId.catalog();

            if (namespace == null || namespace.isEmpty()) {
                return tableNamePred.test(tableId.table());
            }
            return namespacePred.test(tableId.catalog()) && tableNamePred.test(tableId.table());
        }
    }

    /** Match the {@link TableId} against the {@link Selector}s. * */
    public boolean isMatch(TableId tableId) {
        for (Selector selector : selectors) {
            if (selector.isMatch(tableId)) {
                return true;
            }
        }
        return false;
    }

    /** Builder for {@link Selectors}. */
    public static class SelectorsBuilder {

        private List<Selector> selectors;

        /**
         * Current {@link TableId} used in mysql cdc connector will map database name to catalog.
         *
         * @param tableInclusions
         * @return
         */
        public SelectorsBuilder includeTables(String tableInclusions) {

            if (tableInclusions == null || tableInclusions.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid table inclusion pattern cannot be null or empty");
            }

            List<Selector> selectors = new ArrayList<>();
            Set<String> tableSplitSet =
                    Predicates.setOf(
                            tableInclusions, Predicates.RegExSplitterByComma::split, (str) -> str);
            for (String tableSplit : tableSplitSet) {
                List<String> tableIdList =
                        Predicates.listOf(
                                tableSplit, Predicates.RegExSplitterByDot::split, (str) -> str);
                Iterator<String> iterator = tableIdList.iterator();
                if (tableIdList.size() == 1) {
                    selectors.add(new Selector(null, iterator.next()));
                } else if (tableIdList.size() == 2) {
                    selectors.add(new Selector(iterator.next(), iterator.next()));
                } else {
                    throw new IllegalArgumentException(
                            "Invalid table inclusion pattern: " + tableInclusions);
                }
            }
            this.selectors = selectors;
            return this;
        }

        public Selectors build() {
            Selectors selectors = new Selectors();
            selectors.selectors = this.selectors;
            return selectors;
        }
    }
}
