package org.apache.flink.cdc.composer.definition;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Definition of a user-defined function.
 *
 * <p>A transformation definition contains:
 *
 * <ul>
 *   <li>name: Static method name of user-defined functions.
 *   <li>classpath: Fully-qualified class path of package containing given function.
 *   <li>options: Configuration options for the user-defined function.
 * </ul>
 */
public class UdfDef {
    private final String name;
    private final String classpath;
    private final Map<String, String> options;

    public UdfDef(String name, String classpath) {
        this(name, classpath, Collections.emptyMap());
    }

    public UdfDef(String name, String classpath, Map<String, String> options) {
        this.name = name;
        this.classpath = classpath;
        this.options = options != null ? options : Collections.emptyMap();
    }

    public String getName() {
        return name;
    }

    public String getClasspath() {
        return classpath;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UdfDef udfDef = (UdfDef) o;
        return Objects.equals(name, udfDef.name)
                && Objects.equals(classpath, udfDef.classpath)
                && Objects.equals(options, udfDef.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, classpath, options);
    }

    @Override
    public String toString() {
        return "UdfDef{"
                + "name='"
                + name
                + '\''
                + ", classpath='"
                + classpath
                + '\''
                + ", options="
                + options
                + '}';
    }
}
