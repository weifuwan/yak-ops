package io.yak.ops.business.datasource.gateway;

import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor;
import io.yak.ops.common.enums.datasource.DataSourceDbType;

/** Business port for datasource plugin capabilities and descriptor metadata. */
public interface DataSourcePluginGateway {

    /** Resolve the target datasource type from unsaved connection JSON. */
    DataSourceDbType resolveConnectionType(String connectionJson);

    /** Return the Business-owned descriptor projection for one installed plugin. */
    DataSourcePluginDescriptor descriptor(DataSourceDbType dbType);

    /** Parse, validate and normalize connection parameters into Business Domain. */
    ConnectionProfile normalizeConnection(DataSourceDbType dbType, String connectionJson);

    /** Merge masked/missing submitted secrets with the stored normalized connection. */
    String mergeStoredSecrets(DataSourceDbType dbType, String submittedJson, String storedJson);

    /** Test one normalized connection profile. */
    void testConnection(DataSourceDbType dbType, ConnectionProfile connectionProfile, int timeoutSeconds);

    /** Return masked connection JSON for interface projection. */
    String maskConnectionJson(DataSourceDbType dbType, String connectionJson);

    /** Fallback masking for sensitive values embedded in display text/JDBC URLs. */
    String maskSensitiveText(String value);
}
