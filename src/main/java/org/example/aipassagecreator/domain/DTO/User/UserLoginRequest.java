package org.example.aipassagecreator.domain.DTO.User;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginRequest implements Serializable {
    private String userAccount;
    private String userPassword;
    private String uuid;//验证码唯一标识
    private String captcha;//验证码
}
