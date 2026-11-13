package io.yak.ops.modeling.api.vo;

/**
 * A modeling business area exposed to the product shell.
 */
public class ModelingSectionVO {

    private final String code;
    private final String name;

    public ModelingSectionVO(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
