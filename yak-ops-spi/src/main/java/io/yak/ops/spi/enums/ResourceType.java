package io.yak.ops.spi.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

import lombok.Getter;

@Getter
public enum ResourceType {

    /**
     * 0 file
     */
    FILE(0, "file"),
    ALL(2, "all");

    ResourceType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @EnumValue
    private final int code;
    private final String desc;

    public static ResourceType getResourceType(int code) {
        for (ResourceType resourceType : ResourceType.values()) {
            if (resourceType.getCode() == code) {
                return resourceType;
            }
        }
        return null;
    }
}
