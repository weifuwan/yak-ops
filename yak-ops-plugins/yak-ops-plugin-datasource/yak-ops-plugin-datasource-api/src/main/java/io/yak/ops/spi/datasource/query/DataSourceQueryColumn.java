package io.yak.ops.spi.datasource.query;

/** 预览数据的前端列定义。 */
public final class DataSourceQueryColumn {

    private final String title;
    private final String dataIndex;
    private final String key;
    private final boolean ellipsis;

    public DataSourceQueryColumn(String title, String dataIndex, String key, boolean ellipsis) {
        this.title = title;
        this.dataIndex = dataIndex;
        this.key = key;
        this.ellipsis = ellipsis;
    }

    public String getTitle() {
        return title;
    }

    public String getDataIndex() {
        return dataIndex;
    }

    public String getKey() {
        return key;
    }

    public boolean isEllipsis() {
        return ellipsis;
    }
}
