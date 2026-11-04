package io.yak.ops.server.master.config;

import org.springframework.validation.Errors;

import lombok.Data;

@Data
public class CommandFetchStrategy {

    private CommandFetchStrategyType type = CommandFetchStrategyType.ID_SLOT_BASED;

    private CommandFetchConfig config = new IdSlotBasedFetchConfig();

    public void validate(Errors errors) {
        config.validate(errors);
    }

    public enum CommandFetchStrategyType {
        ID_SLOT_BASED,
        ;
    }

    public interface CommandFetchConfig {

        void validate(Errors errors);

    }

    @Data
    public static class IdSlotBasedFetchConfig implements CommandFetchConfig {

        private int idStep = 1;
        private int fetchSize = 10;

        @Override
        public void validate(Errors errors) {
            if (idStep <= 0) {
                errors.rejectValue("step", null, "step must be greater than 0");
            }
            if (fetchSize <= 0) {
                errors.rejectValue("fetchSize", null, "fetchSize must be greater than 0");
            }
        }
    }

}
