package io.yak.ops.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An ordered row of values whose column identity and types belong to its table schema.
 *
 * <p>The list is copied so the fetcher cannot change the row after handing it to the mailbox.
 */
public record RowData(List<Object> values) {

    public RowData {
        values = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(values, "values")));
    }

    public int arity() {
        return values.size();
    }

    public Object getField(int position) {
        return values.get(position);
    }
}
