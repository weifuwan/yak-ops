package io.yak.ops.plugin.registry.jdbc.client;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JdbcRegistryClientIdentify {

    private final Long clientId;

    private final String clientName;

}
