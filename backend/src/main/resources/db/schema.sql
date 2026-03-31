-- =============================================
-- 校园穿搭平台 数据库重构脚本
-- 版本：2.0（全量重写）
-- 生成时间：2026-03-25
-- =============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS campus_outfit DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_outfit;

-- 先删除旧表（按依赖顺序，子表在前）
DROP TABLE IF EXISTS outfit_topic;
DROP TABLE IF EXISTS topic;
DROP TABLE IF EXISTS favorite;
DROP TABLE IF EXISTS outfit_like;
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS follow;
DROP TABLE IF EXISTS outfit;
DROP TABLE IF EXISTS weather_cache;
DROP TABLE IF EXISTS user;

-- =============================================
-- 1. 用户表
-- =============================================
CREATE TABLE user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username      VARCHAR(50)  NOT NULL COMMENT '用户名（可用于登录）',
    email         VARCHAR(100) NULL COMMENT '邮箱（支持邮箱注册/登录）',
    phone         VARCHAR(20)  NULL COMMENT '手机号（支持手机号注册）',
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt加密密码',
    avatar        TEXT         NULL COMMENT '头像URL或Base64',
    nickname      VARCHAR(50)  NULL COMMENT '昵称',
    bio           VARCHAR(200) NULL COMMENT '个人简介',
    gender        TINYINT      NOT NULL COMMENT '性别 (1:男, 2:女)',
    school        VARCHAR(100) NULL COMMENT '所在学校',
    role          VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT '角色：NORMAL/ADMIN',
    follow_count  INT          NOT NULL DEFAULT 0 COMMENT '关注数',
    fan_count     INT          NOT NULL DEFAULT 0 COMMENT '粉丝数',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_phone (phone),
    KEY idx_school (school)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =============================================
-- 2. 穿搭表
-- =============================================
CREATE TABLE outfit (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '穿搭ID',
    user_id         BIGINT        NOT NULL COMMENT '发布用户ID',
    title           VARCHAR(100)  NULL COMMENT '穿搭标题',
    description     TEXT          NULL COMMENT '穿搭描述',
    image_urls      TEXT          NOT NULL COMMENT '图片URL列表（JSON数组，最多9张）',
    thumbnail_url   TEXT          NULL COMMENT '封面缩略图URL',
    ai_analysis     TEXT          NULL COMMENT 'AI分析结果JSON（风格/颜色/单品/建议）',
    style_tags      TEXT          NULL COMMENT '风格标签（JSON数组，如["法式温柔","校园"]）',
    color_tags      TEXT          NULL COMMENT '颜色标签（JSON数组）',
    item_keywords   TEXT          NULL COMMENT '单品关键词（JSON数组）',
    season          VARCHAR(20)   NULL COMMENT '季节：SPRING/SUMMER/AUTUMN/WINTER',
    occasion        VARCHAR(200)  NULL COMMENT '场合标签（JSON数组，如["上课","约会"]）',
    gender          TINYINT       NOT NULL DEFAULT 0 COMMENT '适用性别：0:中性, 1:男款, 2:女款',
    temperature_range VARCHAR(20) NULL COMMENT '适合温度范围（如：15-25°C）',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：PUBLISHED-公开, PRIVATE-私密',
    is_public       TINYINT       NOT NULL DEFAULT 1 COMMENT '是否公开：0私密 1公开',
    like_count      INT           NOT NULL DEFAULT 0 COMMENT '点赞数快照',
    comment_count   INT           NOT NULL DEFAULT 0 COMMENT '评论数快照',
    fav_count       INT           NOT NULL DEFAULT 0 COMMENT '收藏数快照',
    view_count      INT           NOT NULL DEFAULT 0 COMMENT '浏览数',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_season (season),
    KEY idx_status_public (status, is_public),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='穿搭表';

-- =============================================
-- 3. 点赞记录表
-- =============================================
CREATE TABLE outfit_like (
    id            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '点赞ID',
    user_id       BIGINT   NOT NULL COMMENT '点赞用户ID',
    outfit_id     BIGINT   NOT NULL COMMENT '被点赞穿搭ID',
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_like (user_id, outfit_id),
    KEY idx_outfit (outfit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞记录表';

-- =============================================
-- 4. 关注关系表
-- =============================================
CREATE TABLE follow (
    id            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关注记录ID',
    follower_id   BIGINT   NOT NULL COMMENT '关注者用户ID',
    followee_id   BIGINT   NOT NULL COMMENT '被关注者用户ID',
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_follow (follower_id, followee_id),
    KEY idx_followee (followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系表';

-- =============================================
-- 5. 收藏表
-- =============================================
CREATE TABLE favorite (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏记录ID',
    user_id     BIGINT   NOT NULL COMMENT '收藏用户ID',
    outfit_id   BIGINT   NOT NULL COMMENT '收藏穿搭ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite (user_id, outfit_id),
    KEY idx_outfit (outfit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

-- =============================================
-- 6. 评论表（支持楼层回复）
-- =============================================
CREATE TABLE comment (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    outfit_id         BIGINT       NOT NULL COMMENT '关联穿搭ID',
    user_id           BIGINT       NOT NULL COMMENT '评论用户ID',
    parent_id         BIGINT       NULL COMMENT '父评论ID（NULL为一级评论）',
    reply_to_user_id  BIGINT       NULL COMMENT '回复的目标用户ID',
    content           VARCHAR(500) NOT NULL COMMENT '评论内容',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_outfit (outfit_id),
    KEY idx_user (user_id),
    KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- =============================================
-- 6. 话题表
-- =============================================
CREATE TABLE topic (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '话题ID',
    name         VARCHAR(50)  NOT NULL COMMENT '话题名称',
    description  VARCHAR(200) NULL COMMENT '话题描述',
    cover_url    VARCHAR(512) NULL COMMENT '话题封面图URL',
    outfit_count INT          NOT NULL DEFAULT 0 COMMENT '关联穿搭数量',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    is_deleted   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='话题表';

-- =============================================
-- 7. 穿搭-话题关联表
-- =============================================
CREATE TABLE outfit_topic (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    outfit_id   BIGINT   NOT NULL COMMENT '穿搭ID',
    topic_id    BIGINT   NOT NULL COMMENT '话题ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outfit_topic (outfit_id, topic_id),
    KEY idx_topic (topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='穿搭-话题关联表';

-- =============================================
-- 8. 天气缓存表（保留）
-- =============================================
CREATE TABLE weather_cache (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '缓存ID',
    city         VARCHAR(100) NOT NULL COMMENT '城市名称',
    weather_data TEXT         NOT NULL COMMENT '天气数据JSON',
    expire_time  DATETIME     NOT NULL COMMENT '缓存过期时间',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='天气缓存表';

-- =============================================
-- 初始化管理员账号（密码：admin123，BCrypt加密）
-- =============================================
INSERT INTO user (username, email, password, role, school)
VALUES ('admin', 'admin@campus.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt.8G02', 'ADMIN', '校园穿搭平台');

-- =============================================
-- 初始化默认话题
-- =============================================
INSERT INTO topic (name, description) VALUES
('日常校园', '适合每天上课的轻松穿搭'),
('约会穿搭', '浪漫精致的恋爱系穿搭'),
('运动风格', '活力满满的运动穿搭'),
('法式优雅', '简约而不简单的法式美学'),
('街头潮流', '时髦有态度的街头风格'),
('日系清新', '清新治愈的日系穿搭'),
('复古风格', '经典复古的timeless穿搭');

-- =============================================
-- 9. 衣柜单品表
-- =============================================
CREATE TABLE wardrobe_item (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '单品ID',
    user_id             BIGINT        NOT NULL COMMENT '所属用户ID',
    original_image_url  VARCHAR(500)  NULL COMMENT '原始图片URL',
    processed_image_url VARCHAR(500)  NULL COMMENT '处理后图片URL',
    category_main       VARCHAR(50)   NULL COMMENT '主类目',
    category_sub        VARCHAR(50)   NULL COMMENT '子类目',
    color               VARCHAR(50)   NULL COMMENT '颜色',
    material            VARCHAR(50)   NULL COMMENT '材质',
    season              VARCHAR(50)   NULL COMMENT '适合季节',
    ai_raw_tags         TEXT          NULL COMMENT 'AI原始标签',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='衣柜单品表';

-- =============================================
-- 10. AI 穿搭分析记录表
-- =============================================
CREATE TABLE IF NOT EXISTS `ai_analysis_record` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`           BIGINT          NOT NULL COMMENT '所属用户ID',
    `style_name`        VARCHAR(255)    NULL     COMMENT '分析建议风格名称',
    `item_ids`          VARCHAR(1024)   NULL     COMMENT '建议搭配的柜内单品 ID 列表 (JSON 字符串)',
    `result_image_url`  VARCHAR(1024)   NULL     COMMENT 'AI 生成的最终搭配效果图 (MinIO URL)',
    `raw_result_json`   TEXT            NULL     COMMENT '原始对话 JSON 结果',
    `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 穿搭分析记录表';
