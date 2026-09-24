package io.yak.framework.security.permission;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/** Collects provider and annotation declarations after all application beans exist. */
public class PermissionRegistrationInitializer implements SmartInitializingSingleton {
  private final ListableBeanFactory beanFactory;
  private final PermissionRegistrationService registrationService;

  public PermissionRegistrationInitializer(
      ListableBeanFactory beanFactory,
      PermissionRegistrationService registrationService) {
    this.beanFactory = beanFactory;
    this.registrationService = registrationService;
  }

  @Override
  public void afterSingletonsInstantiated() {
    Map<String, GroupBuilder> groups = new LinkedHashMap<>();
    beanFactory.getBeansOfType(PermissionDefinitionProvider.class)
        .values()
        .forEach(provider -> provider.getPermissionDefinitions()
            .forEach(definition -> merge(groups, definition)));

    for (String beanName : beanFactory.getBeanDefinitionNames()) {
      Class<?> type = beanFactory.getType(beanName, false);
      if (type == null) {
        continue;
      }
      Class<?> targetType = ClassUtils.getUserClass(type);
      addAnnotation(
          groups,
          AnnotatedElementUtils.findMergedAnnotation(
              targetType,
              YakPermission.class));
      Map<Method, YakPermission> methods = MethodIntrospector.selectMethods(
          targetType,
          (MethodIntrospector.MetadataLookup<YakPermission>) method ->
              AnnotatedElementUtils.findMergedAnnotation(
                  AopUtils.getMostSpecificMethod(method, targetType),
                  YakPermission.class));
      methods.values().forEach(annotation -> addAnnotation(groups, annotation));
    }
    List<PermissionDefinition> definitions = new ArrayList<>();
    groups.values().forEach(group -> definitions.add(group.build()));
    registrationService.synchronize(definitions);
  }

  private static void addAnnotation(
      Map<String, GroupBuilder> groups,
      YakPermission annotation) {
    if (annotation == null) {
      return;
    }
    String groupCode = StringUtils.hasText(annotation.groupCode())
        ? annotation.groupCode()
        : inferGroupCode(annotation.code());
    merge(
        groups,
        PermissionDefinition.of(
            groupCode,
            annotation.group(),
            PermissionDefinition.Item.ofMenu(
                annotation.code(),
                annotation.name(),
                annotation.description(),
                annotation.menuCode())));
  }

  private static String inferGroupCode(String code) {
    int separator = code.indexOf(':');
    if (separator <= 0) {
      throw new IllegalStateException(
          "Permission '" + code
              + "' must contain ':' or declare groupCode");
    }
    return code.substring(0, separator);
  }

  private static void merge(
      Map<String, GroupBuilder> groups,
      PermissionDefinition definition) {
    groups.computeIfAbsent(
        definition.getCode(),
        code -> new GroupBuilder(code, definition.getName()))
        .add(definition);
  }

  private static final class GroupBuilder {
    private final String code;
    private final String name;
    private final Map<String, PermissionDefinition.Item> items =
        new LinkedHashMap<>();

    private GroupBuilder(String code, String name) {
      this.code = code;
      this.name = name;
    }

    private void add(PermissionDefinition definition) {
      if (!name.equals(definition.getName())) {
        throw new IllegalStateException(
            "Conflicting permission group declaration: " + code);
      }
      for (PermissionDefinition.Item item : definition.getPermissions()) {
        PermissionDefinition.Item old = items.putIfAbsent(
            item.getCode(),
            item);
        if (old != null
            && (!old.getName().equals(item.getName())
                || !Objects.equals(
                    old.getMenuCode(),
                    item.getMenuCode()))) {
          throw new IllegalStateException(
              "Conflicting permission declaration: " + item.getCode());
        }
      }
    }

    private PermissionDefinition build() {
      return PermissionDefinition.fromItems(
          code,
          name,
          new ArrayList<>(items.values()));
    }
  }
}
