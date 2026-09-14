-- =============================================================
-- 内嵌 AI 意图 SDK · 意图三表 DDL（SQL Server 2016+）
--
-- 与 MySQL 版本（deploy/sql/0{2,4,5}-*.sql）语义等价。
-- 说明：bit 保留为 SQL Server 的 bit；datetime 用 datetime2；
--       mediumtext 用 nvarchar(max)；update_time 的 ON UPDATE 语义用触发器实现。
-- =============================================================

-- 1. 意图配置
IF OBJECT_ID(N'dbo.intent_config', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.intent_config (
        id          bigint        IDENTITY(1,1) NOT NULL PRIMARY KEY,
        intent_id   varchar(128)  NOT NULL,
        enabled     bit           NOT NULL CONSTRAINT df_intent_config_enabled  DEFAULT 1,
        roles       varchar(512)  NOT NULL CONSTRAINT df_intent_config_roles    DEFAULT '["*"]',
        remark      varchar(500)  NULL,
        creator     varchar(64)   NULL CONSTRAINT df_intent_config_creator     DEFAULT '',
        create_time datetime2(3)  NOT NULL CONSTRAINT df_intent_config_ctime    DEFAULT SYSDATETIME(),
        updater     varchar(64)   NULL CONSTRAINT df_intent_config_updater     DEFAULT '',
        update_time datetime2(3)  NOT NULL CONSTRAINT df_intent_config_utime    DEFAULT SYSDATETIME(),
        deleted     bit           NOT NULL CONSTRAINT df_intent_config_deleted  DEFAULT 0,
        tenant_id   bigint        NOT NULL CONSTRAINT df_intent_config_tenant   DEFAULT 0
    );
    CREATE INDEX idx_intent_config_intent ON dbo.intent_config (intent_id);
END;
GO

-- 2. 意图规范
IF OBJECT_ID(N'dbo.intent_spec', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.intent_spec (
        id          bigint        IDENTITY(1,1) NOT NULL PRIMARY KEY,
        intent_id   varchar(128)  NOT NULL,
        spec_json   nvarchar(max) NOT NULL,
        source      varchar(16)   NOT NULL CONSTRAINT df_intent_spec_source  DEFAULT 'custom',
        remark      varchar(500)  NULL,
        creator     varchar(64)   NULL CONSTRAINT df_intent_spec_creator     DEFAULT '',
        create_time datetime2(3)  NOT NULL CONSTRAINT df_intent_spec_ctime   DEFAULT SYSDATETIME(),
        updater     varchar(64)   NULL CONSTRAINT df_intent_spec_updater     DEFAULT '',
        update_time datetime2(3)  NOT NULL CONSTRAINT df_intent_spec_utime   DEFAULT SYSDATETIME(),
        deleted     bit           NOT NULL CONSTRAINT df_intent_spec_deleted DEFAULT 0,
        tenant_id   bigint        NOT NULL CONSTRAINT df_intent_spec_tenant  DEFAULT 0
    );
    CREATE INDEX idx_intent_spec_intent ON dbo.intent_spec (intent_id);
END;
GO

-- 3. 执行器档案
IF OBJECT_ID(N'dbo.intent_executor', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.intent_executor (
        id           bigint        IDENTITY(1,1) NOT NULL PRIMARY KEY,
        executor_id  varchar(128)  NOT NULL,
        profile_json nvarchar(max) NOT NULL,
        source       varchar(16)   NOT NULL CONSTRAINT df_intent_exec_source  DEFAULT 'custom',
        remark       varchar(500)  NULL,
        creator      varchar(64)   NULL CONSTRAINT df_intent_exec_creator     DEFAULT '',
        create_time  datetime2(3)  NOT NULL CONSTRAINT df_intent_exec_ctime   DEFAULT SYSDATETIME(),
        updater      varchar(64)   NULL CONSTRAINT df_intent_exec_updater     DEFAULT '',
        update_time  datetime2(3)  NOT NULL CONSTRAINT df_intent_exec_utime   DEFAULT SYSDATETIME(),
        deleted      bit           NOT NULL CONSTRAINT df_intent_exec_deleted DEFAULT 0,
        tenant_id    bigint        NOT NULL CONSTRAINT df_intent_exec_tenant  DEFAULT 0
    );
    CREATE INDEX idx_intent_executor_executor ON dbo.intent_executor (executor_id);
END;
GO

-- 4. update_time 自动维护（等价于 MySQL 的 ON UPDATE CURRENT_TIMESTAMP）
IF OBJECT_ID(N'dbo.trg_intent_config_touch', N'TR') IS NOT NULL DROP TRIGGER dbo.trg_intent_config_touch;
GO
CREATE TRIGGER dbo.trg_intent_config_touch ON dbo.intent_config AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE c SET c.update_time = SYSDATETIME()
    FROM dbo.intent_config c JOIN inserted i ON i.id = c.id;
END;
GO

IF OBJECT_ID(N'dbo.trg_intent_spec_touch', N'TR') IS NOT NULL DROP TRIGGER dbo.trg_intent_spec_touch;
GO
CREATE TRIGGER dbo.trg_intent_spec_touch ON dbo.intent_spec AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE s SET s.update_time = SYSDATETIME()
    FROM dbo.intent_spec s JOIN inserted i ON i.id = s.id;
END;
GO

IF OBJECT_ID(N'dbo.trg_intent_executor_touch', N'TR') IS NOT NULL DROP TRIGGER dbo.trg_intent_executor_touch;
GO
CREATE TRIGGER dbo.trg_intent_executor_touch ON dbo.intent_executor AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE e SET e.update_time = SYSDATETIME()
    FROM dbo.intent_executor e JOIN inserted i ON i.id = e.id;
END;
GO