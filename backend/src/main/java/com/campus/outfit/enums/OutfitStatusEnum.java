package com.campus.outfit.enums;

import lombok.Getter;

/**
 * 穿搭状态枚举
 */
@Getter
public enum OutfitStatusEnum {
    PUBLISHED("PUBLISHED", "已发布"),
    DRAFT("DRAFT", "草稿"),
    DELETED("DELETED", "已删除");

    private final String code;
    private final String desc;

    OutfitStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
