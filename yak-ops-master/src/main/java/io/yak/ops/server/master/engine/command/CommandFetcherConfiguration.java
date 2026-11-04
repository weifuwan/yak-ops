package io.yak.ops.server.master.engine.command;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.dao.repository.CommandDao;
import io.yak.ops.server.master.cluster.MasterSlotManager;
import io.yak.ops.server.master.config.CommandFetchStrategy;
import io.yak.ops.server.master.config.MasterConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommandFetcherConfiguration {

    @Bean
    public ICommandFetcher commandFetcher(MasterConfig masterConfig,
                                          MasterSlotManager masterSlotManager,
                                          CommandDao commandDao) {
        CommandFetchStrategy commandFetchStrategy =
                checkNotNull(masterConfig.getCommandFetchStrategy(), "command fetch strategy is null");
        switch (commandFetchStrategy.getType()) {
            case ID_SLOT_BASED:
                CommandFetchStrategy.IdSlotBasedFetchConfig idSlotBasedFetchConfig =
                        (CommandFetchStrategy.IdSlotBasedFetchConfig) commandFetchStrategy.getConfig();
                return new IdSlotBasedCommandFetcher(idSlotBasedFetchConfig, masterSlotManager, commandDao);
            default:
                throw new IllegalArgumentException(
                        "unsupported command fetch strategy type: " + commandFetchStrategy.getType());
        }
    }
}
