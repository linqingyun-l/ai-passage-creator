package org.example.aipassagecreator.domain.constant;

public interface UserConstant {
    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";
  
    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";
  
    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";

    /**
     * VIP 角色
     */
    String VIP_ROLE = "vip";
    /**
     * 普通用户默认配额
     */
    int DEFAULT_QUOTA = 5;

    /**
     * VIP 用户无限配额
     */
    int VIP_UNLIMITED_QUOTA = -1;
}
