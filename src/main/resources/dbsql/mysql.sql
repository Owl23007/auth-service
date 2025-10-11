-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS auth_service;
USE auth_service;

-- 角色表
CREATE TABLE `role` (
                        `id`          TINYINT UNSIGNED NOT NULL COMMENT '角色ID（雪花ID）',
                        `name`        VARCHAR(32)     NOT NULL UNIQUE COMMENT '角色英文标识，如 USER, ADMIN',
                        `zh_name`     VARCHAR(32)     NOT NULL COMMENT '角色中文名称',
                        `description` VARCHAR(255)    DEFAULT NULL COMMENT '角色描述',
                        `created_at`  DATETIME        DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

-- 初始化角色数据
INSERT INTO `role` (`id`, `name`, `zh_name`, `description`) VALUES
                                                                (0, 'USER', '普通用户', '普通注册用户'),
                                                                (1, 'ADMIN', '管理员', '系统管理员，拥有全部权限');

-- 用户表
CREATE TABLE `user` (
                        `user_id`          BIGINT         NOT NULL COMMENT '用户ID（雪花算法生成）',
                        `username`         VARCHAR(50)    NOT NULL COMMENT '用户名',
                        `email`            VARCHAR(255)   NOT NULL COMMENT '邮箱',
                        `nickname`         VARCHAR(50)    DEFAULT NULL COMMENT '昵称',
                        `phone`            VARCHAR(20)    DEFAULT NULL COMMENT '手机号',
                        `signature`        VARCHAR(255)   DEFAULT NULL COMMENT '个性签名',
                        `avatar_image`     VARCHAR(255)   DEFAULT NULL COMMENT '头像图片URL',
                        `background_image` VARCHAR(255)   DEFAULT NULL COMMENT '背景图片URL',
                        `password`         VARCHAR(255)   NOT NULL COMMENT '密码(加密存储)',
                        `status`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户状态:0-未验证,1-正常,2-封禁,3-停用',
                        `role`             TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '角色ID，引用role表',
                        `create_at`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `update_at`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        PRIMARY KEY (`user_id`),
                        UNIQUE KEY `idx_username` (`username`),
                        UNIQUE KEY `idx_email` (`email`),
                        KEY `idx_nickname` (`nickname`),
                        CONSTRAINT `fk_user_role` FOREIGN KEY (`role`) REFERENCES `role` (`id`)
                            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- Token 表
CREATE TABLE `token` (
                         `jti`                    VARCHAR(128) NOT NULL PRIMARY KEY COMMENT 'JWT ID，全局唯一标识符',
                         `device_id`              VARCHAR(255) NOT NULL COMMENT '实例ID，客户端生成',
                         `user_id`                BIGINT       NOT NULL COMMENT '用户ID',
                         `aud`                    VARCHAR(100) NOT NULL COMMENT 'JWT受众（audience）',
                         `last_login_time`        DATETIME     NOT NULL COMMENT '最后登录时间',
                         `last_login_ip`          VARCHAR(50)  NOT NULL COMMENT '最后登录IP',
                         `last_login_ip_location` VARCHAR(100) DEFAULT NULL COMMENT '最后登录IP地理位置',
                         `expires_at`             DATETIME     NOT NULL COMMENT 'Token过期时间',
                         `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         CONSTRAINT `fk_token_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
                         UNIQUE KEY `idx_user_device` (`user_id`, `device_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户登录Token表';