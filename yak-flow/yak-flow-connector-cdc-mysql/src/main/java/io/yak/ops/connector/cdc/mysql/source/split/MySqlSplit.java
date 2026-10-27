package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.core.api.connector.source.SourceSplit;

/** MySQL CDC split identity shared by initial and latest-offset sources. */
public sealed interface MySqlSplit extends SourceSplit permits MySqlBinlogSplit, MySqlHybridSplit {}
