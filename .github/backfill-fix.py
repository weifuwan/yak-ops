from pathlib import Path
p = Path('yak-flow/yak-flow-connector-cdc-mysql/src/main/java/io/yak/ops/connector/cdc/mysql/source/split/MySqlSnapshotOutput.java')
s = p.read_text()
a=s.index('    @Override\n    public boolean equals(Object other)')
b=s.index('    @Override\n    public String toString()',a)
s=s[:a]+'''    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MySqlSnapshotOutput output) || !low.equals(output.low)
                || !high.equals(output.high) || rows.size() != output.rows.size()) return false;
        // TableRecord deliberately has execution identity, while this checkpoint is a value.
        for (int i = 0; i < rows.size(); i++) {
            TableRecord left = rows.get(i), right = output.rows.get(i);
            if (!left.tableId().equals(right.tableId()) || left.rowKind() != right.rowKind()
                    || !left.row().equals(right.row())) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(low, high);
        for (TableRecord row : rows) hash = 31 * hash + Objects.hash(row.tableId(), row.rowKind(), row.row());
        return hash;
    }

'''+s[b:]
p.write_text(s)
p=Path('yak-flow/yak-flow-connector-cdc-mysql/src/test/java/io/yak/ops/connector/cdc/mysql/source/split/MySqlSnapshotOutputTest.java')
s=p.read_text().replace('assertEquals(original, roundTrip(original));','MySqlSnapshotSplit restored = roundTrip(original);\n        assertEquals(original, restored);\n        assertEquals(original.hashCode(), restored.hashCode());')
p.write_text(s)
