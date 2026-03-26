-- 创建数据库
CREATE DATABASE IF NOT EXISTS campus_outfit CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE campus_outfit;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码',
  `location` VARCHAR(100) DEFAULT NULL COMMENT '地理位置',
  `role` ENUM('NORMAL', 'ADMIN') DEFAULT 'NORMAL' COMMENT '角色',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 穿搭表
CREATE TABLE IF NOT EXISTS `outfit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '穿搭ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `image_url` VARCHAR(255) NOT NULL COMMENT '原图URL',
  `thumbnail_url` VARCHAR(1000) NOT NULL COMMENT '缩略图URL',
  `style_tags` JSON DEFAULT NULL COMMENT '风格标签',
  `temperature_range` VARCHAR(20) DEFAULT NULL COMMENT '温度范围',
  `occasion` VARCHAR(50) DEFAULT NULL COMMENT '场合',
  `is_public` TINYINT NOT NULL DEFAULT 1 COMMENT '是否公开',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_outfit_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='穿搭表';

-- 3. 互动表
CREATE TABLE IF NOT EXISTS `interaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '互动ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `outfit_id` BIGINT NOT NULL COMMENT '穿搭ID',
  `type` ENUM('LIKE', 'FAV', 'COMMENT') NOT NULL COMMENT '互动类型',
  `content` TEXT DEFAULT NULL COMMENT '评论内容',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_outfit_id` (`outfit_id`),
  KEY `idx_type` (`type`),
  CONSTRAINT `fk_interaction_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_interaction_outfit` FOREIGN KEY (`outfit_id`) REFERENCES `outfit` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='互动表';

-- 4. 天气缓存表
CREATE TABLE IF NOT EXISTS `weather_cache` (
  `city_id` VARCHAR(20) NOT NULL COMMENT '城市ID',
  `temperature` INT DEFAULT NULL COMMENT '温度',
  `dress_index` VARCHAR(50) DEFAULT NULL COMMENT '穿衣指数',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`city_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='天气缓存表';

-- 5. 话题表
CREATE TABLE IF NOT EXISTS `topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '话题ID',
  `name` VARCHAR(50) NOT NULL COMMENT '话题名称',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '话题描述',
  `cover_url` VARCHAR(255) DEFAULT NULL COMMENT '话题封面',
  `follow_count` INT DEFAULT 0 COMMENT '关注人数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题表';

-- 6. 穿搭话题关联表
CREATE TABLE IF NOT EXISTS `outfit_topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `outfit_id` BIGINT NOT NULL COMMENT '穿搭ID',
  `topic_id` BIGINT NOT NULL COMMENT '话题ID',
  PRIMARY KEY (`id`),
  KEY `idx_outfit_id` (`outfit_id`),
  KEY `idx_topic_id` (`topic_id`),
  CONSTRAINT `fk_outfit_topic_outfit` FOREIGN KEY (`outfit_id`) REFERENCES `outfit` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_outfit_topic_topic` FOREIGN KEY (`topic_id`) REFERENCES `topic` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='穿搭话题关联表';

-- 7. 点赞记录表
CREATE TABLE IF NOT EXISTS `outfit_like` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '点赞记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `outfit_id` BIGINT NOT NULL COMMENT '穿搭ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_outfit` (`user_id`, `outfit_id`),
  KEY `idx_outfit_id` (`outfit_id`),
  CONSTRAINT `fk_like_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_like_outfit` FOREIGN KEY (`outfit_id`) REFERENCES `outfit` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞记录表';

-- 8. 收藏表
CREATE TABLE IF NOT EXISTS `favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `outfit_id` BIGINT NOT NULL COMMENT '穿搭ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_outfit` (`user_id`, `outfit_id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_favorite_outfit` FOREIGN KEY (`outfit_id`) REFERENCES `outfit` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏表';

-- 9. 评论表
CREATE TABLE IF NOT EXISTS `comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `outfit_id` BIGINT NOT NULL COMMENT '穿搭ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID',
  `reply_to_user_id` BIGINT DEFAULT NULL COMMENT '回复的用户ID',
  `content` TEXT NOT NULL COMMENT '评论内容',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_outfit_id` (`outfit_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_id` (`parent_id`),
  CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comment_outfit` FOREIGN KEY (`outfit_id`) REFERENCES `outfit` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- 10. 关注表
CREATE TABLE IF NOT EXISTS `follow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关注ID',
  `follower_id` BIGINT NOT NULL COMMENT '粉丝ID',
  `following_id` BIGINT NOT NULL COMMENT '被关注者ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_following` (`follower_id`, `following_id`),
  KEY `idx_following_id` (`following_id`),
  CONSTRAINT `fk_follow_follower` FOREIGN KEY (`follower_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_follow_following` FOREIGN KEY (`following_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注表';