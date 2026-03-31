package org.example.aipassagecreator.domain.constant;

import cn.hutool.core.util.ObjUtil;

public enum UserRoleEnum {
    USER("普通用户", "user"),
    ADMIN("管理员", "admin");
    private final String text;
    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /*
     * 根据value获取枚举
     *@Param value 枚举值
     * @Return UserRoleEnum 枚举
     * */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (UserRoleEnum anEnum : UserRoleEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
