-- ============================================================
-- MySQL 业务库初始化（源 A）
-- 生成 1000 条客户主数据
-- ============================================================

-- 关键: mysql:8.0 Docker 默认 character_set_client=latin1，
-- 直接 INSERT 中文会导致 UTF-8 字节被当成 latin1 二次编码后存入 utf8mb4 列（乱码）。
-- 必须在 CREATE/INSERT 前显式 SET NAMES utf8mb4，让客户端、连接、结果集全部走 utf8mb4。
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE DATABASE IF NOT EXISTS customer_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE customer_db;

-- ------------------------------------------------------------
-- 客户主数据表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS customer;
CREATE TABLE customer (
  cust_id        VARCHAR(32)  PRIMARY KEY COMMENT '客户编号 C0001-C1000',
  cust_name      VARCHAR(64)  NOT NULL    COMMENT '客户姓名',
  phone          VARCHAR(20)              COMMENT '手机号(PII)',
  id_card        VARCHAR(18)              COMMENT '身份证号(PII)',
  cust_level     VARCHAR(16)              COMMENT '客户等级: VIP1/VIP2/VIP3',
  region         VARCHAR(32)              COMMENT '区域: 华东/华南/华北/华西/华中',
  register_time  DATETIME                 COMMENT '注册时间',
  INDEX idx_level_region (cust_level, region),
  INDEX idx_register (register_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='客户主数据表';

-- ------------------------------------------------------------
-- 用 information_schema 生成序列，避免硬编码 1000 条 INSERT
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS generate_customers;
DELIMITER $$
CREATE PROCEDURE generate_customers()
BEGIN
  DECLARE i INT DEFAULT 1;
  DECLARE v_name VARCHAR(64);
  DECLARE v_phone VARCHAR(20);
  DECLARE v_idcard VARCHAR(18);
  DECLARE v_level VARCHAR(16);
  DECLARE v_region VARCHAR(32);

  -- 50 个常用中文姓名
  WHILE i <= 1000 DO
    SET v_name    = ELT(1 + (i % 50),
                        '张伟','李娜','王芳','刘洋','陈杰','杨帆','赵磊','黄涛','周敏','吴婷',
                        '徐丽','孙强','马超','朱琳','胡静','郭鹏','林峰','何静','高翔','罗敏',
                        '郑爽','梁宇','谢俊','宋佳','唐文','韩雪','冯刚','邓超','曹颖','彭涛',
                        '曾毅','汪涵','董洁','潘虹','袁泉','于和伟','蒋欣','余男','杜淳','李晨',
                        '沈腾','王菲','郑爽','姚晨','宁静','海清','马伊琍','陈数','白百何','王珞丹');

    SET v_phone   = CONCAT('1',
                           ELT(1 + (i % 9), '3','3','3','5','6','7','8','8','9'),
                           LPAD(MOD(FLOOR(RAND() * 1000000000), 1000000000), 9, '0'));

    SET v_idcard  = CONCAT(
                      LPAD(1 + (i % 65), 6, '0'),              -- 地区码 6位
                      '19', LPAD(70 + (i % 30), 2, '0'),       -- 年 4位 (1970-1999)
                      LPAD(1 + (i % 12), 2, '0'),              -- 月 2位
                      LPAD(1 + (i % 28), 2, '0'),              -- 日 2位
                      LPAD(MOD(i * 7 + 13, 1000), 3, '0'),     -- 序号 3位
                      ELT(1 + (i % 10),'0','1','2','3','4','5','6','7','8','9')); -- 校验位 1位
                                                            -- 总长 = 6+4+2+2+3+1 = 18

    SET v_level   = ELT(1 + (i % 3), 'VIP1','VIP2','VIP3');

    SET v_region  = ELT(1 + (i % 5), '华东','华南','华北','华西','华中');

    INSERT INTO customer (cust_id, cust_name, phone, id_card,
                          cust_level, region, register_time)
    VALUES (
      CONCAT('C', LPAD(i, 4, '0')),
      v_name,
      v_phone,
      v_idcard,
      v_level,
      v_region,
      DATE_SUB(NOW(), INTERVAL (i % 1095) DAY) + INTERVAL FLOOR(RAND() * 24) HOUR
    );

    SET i = i + 1;
  END WHILE;
END$$
DELIMITER ;

CALL generate_customers();
DROP PROCEDURE generate_customers;

-- ------------------------------------------------------------
-- 验证：1000 行
-- ------------------------------------------------------------
SELECT 'customers inserted' AS step, COUNT(*) AS count FROM customer;

-- 抽样查看
SELECT cust_id, cust_name, phone, cust_level, region, register_time
FROM customer
ORDER BY cust_id
LIMIT 10;
