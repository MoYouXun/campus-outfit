-- 创建数据库
CREATE DATABASE IF NOT EXISTS campus_outfit CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE campus_outfit;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `avatar` VARCHAR(512) DEFAULT NULL COMMENT '头像',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `bio` VARCHAR(200) DEFAULT NULL COMMENT '个人简介',
  `gender` VARCHAR(10) DEFAULT NULL COMMENT '性别',
  `school` VARCHAR(100) DEFAULT NULL COMMENT '所在学校',
  `location` VARCHAR(100) DEFAULT NULL COMMENT '地理位置',
  `role` ENUM('NORMAL', 'ADMIN') DEFAULT 'NORMAL' COMMENT '角色',
  `follow_count` INT NOT NULL DEFAULT 0 COMMENT '关注数',
  `fan_count` INT NOT NULL DEFAULT 0 COMMENT '粉丝数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 穿搭表
CREATE TABLE IF NOT EXISTS `outfit` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '穿搭ID',
    `user_id`         BIGINT        NOT NULL COMMENT '发布用户ID',
    `title`           VARCHAR(100)  NULL COMMENT '穿搭标题',
    `description`     TEXT          NULL COMMENT '穿搭描述',
    `image_urls`      TEXT          NOT NULL COMMENT '图片URL列表（JSON数组，最多9张）',
    `thumbnail_url`   TEXT          NULL COMMENT '封面缩略图URL',
    `ai_analysis`     TEXT          NULL COMMENT 'AI分析结果JSON（风格/颜色/单品/建议）',
    `style_tags`      TEXT          NULL COMMENT '风格标签（JSON数组，如["法式温柔","校园"]）',
    `color_tags`      TEXT          NULL COMMENT '颜色标签（JSON数组）',
    `item_keywords`   TEXT          NULL COMMENT '单品关键词（JSON数组）',
    `season`          VARCHAR(20)   NULL COMMENT '季节：SPRING/SUMMER/AUTUMN/WINTER',
    `occasion`        VARCHAR(200)  NULL COMMENT '场合标签（JSON数组，如["上课","约会"]）',
    `temperature_range` VARCHAR(20) NULL COMMENT '适合温度范围（如：15-25°C）',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：PUBLISHED-公开, PRIVATE-私密',
    `is_public`       TINYINT       NOT NULL DEFAULT 1 COMMENT '是否公开：0私密 1公开',
    `like_count`      INT           NOT NULL DEFAULT 0 COMMENT '点赞数快照',
    `comment_count`   INT           NOT NULL DEFAULT 0 COMMENT '评论数快照',
    `fav_count`       INT           NOT NULL DEFAULT 0 COMMENT '收藏数快照',
    `view_count`      INT           NOT NULL DEFAULT 0 COMMENT '浏览数',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_season` (`season`),
    KEY `idx_status_public` (`status`, `is_public`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='穿搭表';