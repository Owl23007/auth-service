create database auth_service;

use auth_service;
CREATE TABLE `user` (
                        `user_id` int(11) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                        `username` varchar(50) NOT NULL COMMENT '用户名',
                        `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
                        `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
                        `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
                        `signature` varchar(255) DEFAULT NULL COMMENT '个性签名',
                        `avatar_image` varchar(255) DEFAULT NULL COMMENT '头像图片URL',
                        `background_image` varchar(255) DEFAULT NULL COMMENT '背景图片URL',
                        `password` varchar(100) NOT NULL COMMENT '密码(加密存储)',
                        `status` int(2) NOT NULL DEFAULT 0 COMMENT '用户状态:0-未验证,1-正常,2-封禁,3-停用',
                         `role` int(2) NOT NULL DEFAULT 0 COMMENT '用户角色:0-普通用户,1-管理员',
                        `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
                        `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
                        `last_login_ip_location` varchar(100) DEFAULT NULL COMMENT '最后登录IP位置',
                        `last_login_device` varchar(255) DEFAULT NULL COMMENT '最后登录设备信息',
                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        PRIMARY KEY (`user_id`),
                        UNIQUE KEY `idx_username` (`username`),
                        KEY `idx_email` (`email`),
                        KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 添加激活令牌表
CREATE TABLE `token` (
    `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `email` varchar(100) NOT NULL COMMENT '邮箱',
    `token` varchar(255) NOT NULL COMMENT '激活令牌',
    `expires_at` datetime NOT NULL COMMENT '过期时间',
    `is_used` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已使用：0-未使用，1-已使用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_token` (`token`),
    KEY `idx_email` (`email`),
    KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='令牌表';
