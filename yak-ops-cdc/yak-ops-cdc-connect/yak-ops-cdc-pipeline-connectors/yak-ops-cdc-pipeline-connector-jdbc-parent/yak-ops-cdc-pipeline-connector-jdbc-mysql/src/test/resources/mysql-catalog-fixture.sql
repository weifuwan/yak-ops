-- Run in an empty test MySQL instance. Connect with yak_catalog_source as the default database.
CREATE DATABASE yak_catalog_source;
CREATE DATABASE yak_e2e_target;
CREATE TABLE yak_catalog_source.orders (other_id BIGINT PRIMARY KEY);
CREATE TABLE yak_catalog_source.source_only (id INT);
CREATE TABLE yak_e2e_target.orders (
    id BIGINT PRIMARY KEY,
    elapsed TIME(3) NULL,
    created DATETIME(6) NULL,
    updated TIMESTAMP(6) NULL
);
