package io.yak.ops.connector.jdbc.database.connection;

/**
 * Identifies the authentication mechanism for an SSH tunnel.
 */
public enum SshAuthType {

    /** Authenticates using a username and password. */
    PASSWORD,

    /** Authenticates using a username and private key. */
    PRIVATE_KEY
}
