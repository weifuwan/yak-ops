package org.apache.flink.cdc.common.route;

import java.io.Serializable;

/** Definition of a routing rule with replacement symbol. */
public class RouteRule implements Serializable {

    private static final long serialVersionUID = 1L;

    public RouteRule(String sourceTable, String sinkTable) {
        this(sourceTable, sinkTable, null);
    }

    public RouteRule(String sourceTable, String sinkTable, String replaceSymbol) {
        this.sourceTable = sourceTable;
        this.sinkTable = sinkTable;
        this.replaceSymbol = replaceSymbol;
    }

    public String sourceTable;
    public String sinkTable;
    public String replaceSymbol;
}
