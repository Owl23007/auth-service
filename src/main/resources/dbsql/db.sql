CREATE TABLE "role" (
                        id          SMALLINT    NOT NULL,
                        name        VARCHAR(32) NOT NULL,
                        zh_name     VARCHAR(32) NOT NULL,
                        description VARCHAR(255),
                        created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        UNIQUE (name)
);

-- 添加注释
COMMENT ON TABLE "role" IS '角色表';
COMMENT ON COLUMN "role".id IS '角色ID';
COMMENT ON COLUMN "role".name IS '角色英文标识，如 USER, ADMIN';
COMMENT ON COLUMN "role".zh_name IS '角色中文名称';
COMMENT ON COLUMN "role".description IS '角色描述';

-- 初始化角色数据
INSERT INTO "role" (id, name, zh_name, description) VALUES
                                                        (0, 'USER', '普通用户', '普通注册用户'),
                                                        (1, 'ADMIN', '管理员', '系统管理员，拥有全部权限');

-- 用户表
CREATE TABLE users (
                        user_id          BIGINT       NOT NULL,
                        username         VARCHAR(50)  NOT NULL,
                        email            VARCHAR(255) NOT NULL,
                        nickname         VARCHAR(50),
                        phone            VARCHAR(20),
                        signature        VARCHAR(255),
                        avatar_image     VARCHAR(255),
                        background_image VARCHAR(255),
                        password         VARCHAR(255) NOT NULL,
                        status           SMALLINT     NOT NULL DEFAULT 0,
                        role             SMALLINT     NOT NULL DEFAULT 0,
                        create_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (user_id),
                        UNIQUE (username),
                        UNIQUE (email)
);

-- 添加外键约束（PG 支持，但需确保类型一致）
ALTER TABLE users
    ADD CONSTRAINT fk_user_role
        FOREIGN KEY (role) REFERENCES "role"(id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 添加索引（PG 中 UNIQUE 已隐含索引，普通索引需显式建）
CREATE INDEX idx_user_nickname ON users (nickname);

-- 添加注释
COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.user_id IS '用户ID（雪花算法生成）';
COMMENT ON COLUMN users.username IS '用户名';
COMMENT ON COLUMN users.email IS '邮箱';
COMMENT ON COLUMN users.nickname IS '昵称';
COMMENT ON COLUMN users.phone IS '手机号';
COMMENT ON COLUMN users.signature IS '个性签名';
COMMENT ON COLUMN users.avatar_image IS '头像图片URL';
COMMENT ON COLUMN users.background_image IS '背景图片URL';
COMMENT ON COLUMN users.password IS '密码(加密存储)';
COMMENT ON COLUMN users.status IS '用户状态:0-未验证,1-正常,2-封禁,3-停用';
COMMENT ON COLUMN users.role IS '角色ID，引用role表';
COMMENT ON COLUMN users.create_at IS '创建时间';
COMMENT ON COLUMN users.update_at IS '更新时间';

-- 自动更新 update_at 的触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- 绑定触发器到 user 表
CREATE TRIGGER trg_user_update_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Token 表
CREATE TABLE tokens (
                         jti                    VARCHAR(128) NOT NULL PRIMARY KEY,
                         device_id              VARCHAR(255) NOT NULL,
                         user_id                BIGINT       NOT NULL,
                         aud                    VARCHAR(100) NOT NULL,
                         last_login_time        TIMESTAMP    NOT NULL,
                         last_login_ip          VARCHAR(50)  NOT NULL,
                         last_login_ip_location VARCHAR(100),
                         expires_at             TIMESTAMP    NOT NULL,
                         created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 外键
ALTER TABLE tokens
    ADD CONSTRAINT fk_token_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
            ON DELETE CASCADE;

-- 唯一索引
CREATE UNIQUE INDEX idx_user_device ON tokens (user_id, device_id);

-- 注释
COMMENT ON TABLE tokens IS '用户登录Token表';
COMMENT ON COLUMN tokens.jti IS 'JWT ID，全局唯一标识符';
COMMENT ON COLUMN tokens.device_id IS '实例ID，客户端生成';
COMMENT ON COLUMN tokens.user_id IS '用户ID';
COMMENT ON COLUMN tokens.aud IS 'JWT受众（audience）';
COMMENT ON COLUMN tokens.last_login_time IS '最后登录时间';
COMMENT ON COLUMN tokens.last_login_ip IS '最后登录IP';
COMMENT ON COLUMN tokens.last_login_ip_location IS '最后登录IP地理位置';
COMMENT ON COLUMN tokens.expires_at IS 'Token过期时间';
COMMENT ON COLUMN tokens.created_at IS '创建时间';