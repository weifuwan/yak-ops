package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.AlertEvent;
import io.yak.ops.common.enums.AlertWarnLevel;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ServerAlertContent {

    /**
     * server type :master or worker
     */
    @JsonProperty("type")
    final String type;
    @JsonProperty("host")
    final String host;
    @JsonProperty("event")
    final AlertEvent event;
    @JsonProperty("warningLevel")
    final AlertWarnLevel warningLevel;

    private ServerAlertContent(Builder builder) {
        this.type = builder.type;
        this.host = builder.host;
        this.event = builder.event;
        this.warningLevel = builder.warningLevel;

    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private String type;

        private String host;

        private AlertEvent event;

        private AlertWarnLevel warningLevel;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder event(AlertEvent event) {
            this.event = event;
            return this;
        }

        public Builder warningLevel(AlertWarnLevel warningLevel) {
            this.warningLevel = warningLevel;
            return this;
        }

        public ServerAlertContent build() {
            return new ServerAlertContent(this);
        }
    }

}
