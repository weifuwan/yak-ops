package io.yak.ops.configuration;

import com.fasterxml.jackson.databind.JavaType;
import java.util.List;

/** An option constrained to a nonempty set of permissible values. */
public final class SingleChoiceOption<T> extends Option<T> {

    SingleChoiceOption(String key, JavaType type, T value, boolean hasDefault, List<T> allowedValues) {
        super(key, type, value, hasDefault, "", allowedValues);
    }
}
