package io.yak.framework.security.permission;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Supplies permissions that cannot conveniently be declared on a controller. */
@FunctionalInterface
public interface PermissionDefinitionProvider {
  List<PermissionDefinition> getPermissionDefinitions();

  static PermissionDefinitionProvider of(PermissionDefinition... definitions) {
    List<PermissionDefinition> values = definitions == null
        ? Collections.emptyList() : List.copyOf(Arrays.asList(definitions));
    return () -> values;
  }
}
