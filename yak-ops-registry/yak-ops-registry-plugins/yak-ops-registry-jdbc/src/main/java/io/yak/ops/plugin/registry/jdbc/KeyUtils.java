package io.yak.ops.plugin.registry.jdbc;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.registry.api.RegistryConstants;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KeyUtils {

    /**
     * Whether the path is the parent path of the child
     * <p> Only the parentPath is the parent path of the childPath, return true
     * <p> If the parentPath is equal to the childPath, return false
     */
    public static boolean isParent(final String parentPath, final String childPath) {
        if (StringUtils.isEmpty(parentPath)) {
            throw new IllegalArgumentException("Invalid parent path " + parentPath);
        }
        if (StringUtils.isEmpty(childPath)) {
            throw new IllegalArgumentException("Invalid child path " + childPath);
        }
        final String[] parentSplit = removeLastSlash(parentPath).split(RegistryConstants.PATH_SEPARATOR);
        final String[] childSplit = removeLastSlash(childPath).split(RegistryConstants.PATH_SEPARATOR);
        // If the parent path is longer than or equals the child path, it is impossible to be the parent path of the
        // child path
        if (parentSplit.length >= childSplit.length) {
            return false;
        }
        for (int i = 0; i < parentSplit.length; i++) {
            if (!parentSplit[i].equals(childSplit[i])) {
                return false;
            }
        }
        return true;

    }

    public static boolean isSamePath(final String path1, final String path2) {
        return removeLastSlash(path1).equals(path2);
    }

    private static String removeLastSlash(final String path) {
        checkNotNull(path, "path is null");
        if (!path.startsWith(RegistryConstants.PATH_SEPARATOR)) {
            throw new IllegalArgumentException("Invalid path " + path);
        }
        int length = path.length() - 1;
        while (length >= 0 && path.charAt(length) == RegistryConstants.PATH_SEPARATOR_CHAR) {
            length--;
        }
        if (length == -1) {
            return RegistryConstants.PATH_SEPARATOR;
        }
        return path.substring(0, length + 1);
    }

}
