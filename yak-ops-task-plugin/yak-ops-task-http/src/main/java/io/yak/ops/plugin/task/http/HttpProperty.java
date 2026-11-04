package io.yak.ops.plugin.task.http;

import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HttpProperty {

    private String prop;

    private HttpParametersType httpParametersType;

    private String value;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HttpProperty property = (HttpProperty) o;
        return Objects.equals(prop, property.prop)
                && Objects.equals(value, property.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prop, value);
    }

}
