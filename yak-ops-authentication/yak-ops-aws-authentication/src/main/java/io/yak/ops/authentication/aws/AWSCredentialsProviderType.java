package io.yak.ops.authentication.aws;

import java.util.Optional;

import lombok.Getter;

@Getter
public enum AWSCredentialsProviderType {

    STATIC_CREDENTIALS_PROVIDER("AWSStaticCredentialsProvider"),
    INSTANCE_PROFILE_CREDENTIALS_PROVIDER("InstanceProfileCredentialsProvider"),
    ;

    private final String name;

    AWSCredentialsProviderType(String name) {
        this.name = name;
    }

    public static Optional<AWSCredentialsProviderType> of(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (AWSCredentialsProviderType type : values()) {
            if (type.getName().equalsIgnoreCase(name)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

}
