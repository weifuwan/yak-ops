package io.yak.ops.api.dto;

import io.yak.ops.common.enums.UserType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LdapLoginResult {

    private boolean success;
    private String ldapEmail;
    private UserType userType;
    private String userName;
}
