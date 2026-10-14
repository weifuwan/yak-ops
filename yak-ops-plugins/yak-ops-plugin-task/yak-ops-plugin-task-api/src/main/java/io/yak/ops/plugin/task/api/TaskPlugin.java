package io.yak.ops.plugin.task.api;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;

/**
 * Defines the configuration contract for one registered Task type.
 *
 * <p>Parsing and static validation do not access Datasource. Resource validation uses only
 * authorized Workspace metadata and does not mutate persisted configuration.
 */
public interface TaskPlugin {

    /** Stable type identifier, matched exactly in persisted Task Definitions. */
    String type();

    /** Schema version supported by the plugin's persisted configuration. */
    int parameterSchemaVersion();

    /** Declares task-level options independently of Connector-specific rules. */
    OptionRule optionRule();

    /** Parses and statically validates persisted JSON as an immutable configuration. */
    ReadonlyConfig parse(String content);

    /**
     * Parses an immutable persisted definition using its recorded parameter schema version.
     *
     * @param schemaVersion version recorded on the Definition Version
     * @param content persisted parameter JSON
     * @return statically validated, immutable configuration
     */
    default ReadonlyConfig parse(int schemaVersion, String content) {
        if (schemaVersion != parameterSchemaVersion()) {
            throw new TaskPluginException("Unsupported Task parameter schema version");
        }
        return parse(content);
    }

    /**
     * Validates a change between two already parsed versions before a new version is appended.
     *
     * @param previous stored configuration interpreted with its own schema version
     * @param updated new configuration using the current schema version
     * @param context authorized Workspace resource view
     */
    default void validateUpdate(ReadonlyConfig previous, ReadonlyConfig updated, TaskValidationContext context) {}

    /**
     * Checks Workspace-authorized resource references without reading external table schemas.
     *
     * @param parameters locally validated configuration
     * @param context Workspace-scoped read-only resource view
     */
    void validateReferences(ReadonlyConfig parameters, TaskValidationContext context);

    /** Validates resources referenced by an already parsed Task configuration. */
    void validate(ReadonlyConfig parameters, TaskValidationContext context);
}
