package io.yak.ops.spi.params.radio;

import static io.yak.ops.common.constants.Constants.STRING_PLUGIN_PARAM_OPTIONS;
import static io.yak.ops.spi.params.base.FormType.RADIO;

import io.yak.ops.spi.params.base.ParamsOptions;
import io.yak.ops.spi.params.base.PluginParams;
import io.yak.ops.spi.params.base.Validate;

import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * front-end radio select component
 */
public class RadioParam extends PluginParams {

    @JsonProperty(STRING_PLUGIN_PARAM_OPTIONS)
    private List<ParamsOptions> options;

    private RadioParamProps props;

    private RadioParam(Builder builder) {
        super(builder);
        this.options = builder.options;
    }

    public static Builder newBuilder(String name, String title) {
        return new Builder(name, title);
    }

    public static class Builder extends PluginParams.Builder {

        public Builder(String name, String title) {
            super(name, RADIO, title);
        }

        private List<ParamsOptions> options;

        private RadioParamProps props;

        public Builder setOptions(List<ParamsOptions> options) {
            this.options = options;
            return this;
        }

        public Builder addParamsOptions(ParamsOptions paramsOptions) {
            if (this.options == null) {
                this.options = new LinkedList<>();
            }

            this.options.add(paramsOptions);
            return this;
        }

        public Builder setProps(RadioParamProps props) {
            this.props = props;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setInfo(String info) {
            this.info = info;
            return this;
        }

        public Builder setValue(Object value) {
            this.value = value;
            return this;
        }

        public Builder setValidateList(List<Validate> validateList) {
            this.validateList = validateList;
            return this;
        }

        public Builder addValidate(Validate validate) {
            if (this.validateList == null) {
                this.validateList = new LinkedList<>();
            }
            this.validateList.add(validate);
            return this;
        }

        public Builder setHidden(Boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder setDisplay(Boolean display) {
            this.display = display;
            return this;
        }

        @Override
        public RadioParam build() {
            return new RadioParam(this);
        }
    }

    public List<ParamsOptions> getOptions() {
        return options;
    }

    @Override
    public RadioParamProps getProps() {
        return props;
    }
}
