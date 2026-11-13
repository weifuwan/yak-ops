package io.yak.ops.modeling.support;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds tree structures from flat records using key/parentId/children properties.
 */
@Slf4j
public class TreeUtil<T> {

    public List<T> getTreeByList(List<T> list) {
        return getTreeByList(list, null, "key", "parentId", "children", false);
    }

    public List<T> getTreeByList(
            List<T> list, String root, String key, String parent, String children, boolean ignoreOrphans) {
        Map<String, T> nodes = new HashMap<>();
        Map<String, List<T>> childMap = new HashMap<>();
        for (T item : list) {
            String id = stringValue(read(item, key));
            if (id != null) {
                nodes.put(id, item);
            }
        }
        List<T> roots = new ArrayList<>();
        for (T item : list) {
            String id = stringValue(read(item, key));
            String parentId = stringValue(read(item, parent));
            write(item, children, new ArrayList<T>());
            if (parentId == null || parentId.isEmpty() || (root != null && root.equals(parentId))) {
                roots.add(item);
            } else if (nodes.containsKey(parentId)) {
                childMap.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(item);
            } else if (!ignoreOrphans) {
                roots.add(item);
            }
        }
        childMap.forEach((parentId, childrenList) -> write(nodes.get(parentId), children, childrenList));
        return roots;
    }

    private Object read(Object target, String property) {
        try {
            PropertyDescriptor descriptor = new PropertyDescriptor(property, target.getClass());
            Method getter = descriptor.getReadMethod();
            return getter.invoke(target);
        } catch (Exception ex) {
            log.debug("Unable to read tree property {}", property, ex);
            return null;
        }
    }

    private void write(Object target, String property, Object value) {
        try {
            PropertyDescriptor descriptor = new PropertyDescriptor(property, target.getClass());
            descriptor.getWriteMethod().invoke(target, value);
        } catch (Exception ex) {
            log.debug("Unable to write tree property {}", property, ex);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
