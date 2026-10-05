-- ============================================================
-- 苍穹外卖网页版数据库（个人练习项目）
-- 基于 sky_take_out 表结构改造：
--   1. user 表去掉 openid，增加 username/password/role
--   2. 写入演示数据：分类、菜品、套餐、口味、账号
-- 执行：mysql -u root -p < sky_web.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS sky_web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
USE sky_web;

DROP TABLE IF EXISTS `address_book`;
CREATE TABLE `address_book` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '用户id',
  `consignee` varchar(50) DEFAULT NULL COMMENT '收货人',
  `sex` varchar(2) DEFAULT NULL COMMENT '性别',
  `phone` varchar(11) NOT NULL COMMENT '手机号',
  `province_code` varchar(12) DEFAULT NULL COMMENT '省级区划编号',
  `province_name` varchar(32) DEFAULT NULL COMMENT '省级名称',
  `city_code` varchar(12) DEFAULT NULL COMMENT '市级区划编号',
  `city_name` varchar(32) DEFAULT NULL COMMENT '市级名称',
  `district_code` varchar(12) DEFAULT NULL COMMENT '区级区划编号',
  `district_name` varchar(32) DEFAULT NULL COMMENT '区级名称',
  `detail` varchar(200) DEFAULT NULL COMMENT '详细地址',
  `label` varchar(100) DEFAULT NULL COMMENT '标签',
  `is_default` tinyint(1) NOT NULL DEFAULT 0 COMMENT '默认 0 否 1是',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 DEFAULT CHARSET = utf8mb4 COMMENT = '地址簿';

DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` int(11) DEFAULT NULL COMMENT '类型 1 菜品分类 2 套餐分类',
  `name` varchar(32) NOT NULL COMMENT '分类名称',
  `sort` int(11) NOT NULL DEFAULT 0 COMMENT '顺序',
  `status` int(11) DEFAULT NULL COMMENT '分类状态 0禁用 1启用',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_user` bigint(20) DEFAULT NULL,
  `update_user` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_category_name` (`name`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 30 DEFAULT CHARSET = utf8mb4 COMMENT = '菜品及套餐分类';

DROP TABLE IF EXISTS `dish`;
CREATE TABLE `dish` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) NOT NULL COMMENT '菜品名称',
  `category_id` bigint(20) NOT NULL COMMENT '菜品分类id',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品价格',
  `image` varchar(255) DEFAULT NULL COMMENT '图片',
  `description` varchar(255) DEFAULT NULL COMMENT '描述信息',
  `status` int(11) DEFAULT 1 COMMENT '0 停售 1 起售',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_user` bigint(20) DEFAULT NULL,
  `update_user` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_dish_name` (`name`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 80 DEFAULT CHARSET = utf8mb4 COMMENT = '菜品';

DROP TABLE IF EXISTS `dish_flavor`;
CREATE TABLE `dish_flavor` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dish_id` bigint(20) NOT NULL COMMENT '菜品',
  `name` varchar(32) DEFAULT NULL COMMENT '口味名称',
  `value` varchar(255) DEFAULT NULL COMMENT '口味数据list',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 130 DEFAULT CHARSET = utf8mb4 COMMENT = '菜品口味关系表';

DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) NOT NULL COMMENT '姓名',
  `username` varchar(32) NOT NULL COMMENT '用户名',
  `password` varchar(64) NOT NULL COMMENT '密码',
  `phone` varchar(11) NOT NULL COMMENT '手机号',
  `sex` varchar(2) NOT NULL COMMENT '性别',
  `id_number` varchar(18) NOT NULL COMMENT '身份证号',
  `status` int(11) NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_user` bigint(20) DEFAULT NULL,
  `update_user` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_username` (`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 DEFAULT CHARSET = utf8mb4 COMMENT = '员工信息';

DROP TABLE IF EXISTS `order_detail`;
CREATE TABLE `order_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) DEFAULT NULL COMMENT '名字',
  `image` varchar(255) DEFAULT NULL COMMENT '图片',
  `order_id` bigint(20) NOT NULL COMMENT '订单id',
  `dish_id` bigint(20) DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint(20) DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) DEFAULT NULL COMMENT '口味',
  `number` int(11) NOT NULL DEFAULT 1 COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 30 DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细表';

DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `number` varchar(50) DEFAULT NULL COMMENT '订单号',
  `status` int(11) NOT NULL DEFAULT 1 COMMENT '订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款',
  `user_id` bigint(20) NOT NULL COMMENT '下单用户',
  `address_book_id` bigint(20) NOT NULL COMMENT '地址id',
  `order_time` datetime NOT NULL COMMENT '下单时间',
  `checkout_time` datetime DEFAULT NULL COMMENT '结账时间',
  `pay_method` int(11) NOT NULL DEFAULT 1 COMMENT '支付方式 1模拟支付 2余额支付',
  `pay_status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '支付状态 0未支付 1已支付 2退款',
  `amount` decimal(10,2) NOT NULL COMMENT '实收金额',
  `remark` varchar(100) DEFAULT NULL COMMENT '备注',
  `phone` varchar(11) DEFAULT NULL COMMENT '手机号',
  `address` varchar(255) DEFAULT NULL COMMENT '地址',
  `user_name` varchar(32) DEFAULT NULL COMMENT '用户名称',
  `consignee` varchar(32) DEFAULT NULL COMMENT '收货人',
  `cancel_reason` varchar(255) DEFAULT NULL COMMENT '订单取消原因',
  `rejection_reason` varchar(255) DEFAULT NULL COMMENT '订单拒绝原因',
  `cancel_time` datetime DEFAULT NULL COMMENT '订单取消时间',
  `estimated_delivery_time` datetime DEFAULT NULL COMMENT '预计送达时间',
  `delivery_status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '配送状态 1立即送出 0选择具体时间',
  `delivery_time` datetime DEFAULT NULL COMMENT '送达时间',
  `pack_amount` int(11) DEFAULT NULL COMMENT '打包费',
  `tableware_number` int(11) DEFAULT NULL COMMENT '餐具数量',
  `tableware_status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '餐具数量状态 1按餐量提供 0选择具体数量',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 30 DEFAULT CHARSET = utf8mb4 COMMENT = '订单表';

DROP TABLE IF EXISTS `setmeal`;
CREATE TABLE `setmeal` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id` bigint(20) NOT NULL COMMENT '菜品分类id',
  `name` varchar(32) NOT NULL COMMENT '套餐名称',
  `price` decimal(10,2) NOT NULL COMMENT '套餐价格',
  `status` int(11) DEFAULT 1 COMMENT '售卖状态 0停售 1起售',
  `description` varchar(255) DEFAULT NULL COMMENT '描述信息',
  `image` varchar(255) DEFAULT NULL COMMENT '图片',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_user` bigint(20) DEFAULT NULL,
  `update_user` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_setmeal_name` (`name`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 40 DEFAULT CHARSET = utf8mb4 COMMENT = '套餐';

DROP TABLE IF EXISTS `setmeal_dish`;
CREATE TABLE `setmeal_dish` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `setmeal_id` bigint(20) DEFAULT NULL COMMENT '套餐id',
  `dish_id` bigint(20) DEFAULT NULL COMMENT '菜品id',
  `name` varchar(32) DEFAULT NULL COMMENT '菜品名称（冗余字段）',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品单价（冗余字段）',
  `copies` int(11) DEFAULT NULL COMMENT '菜品份数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 60 DEFAULT CHARSET = utf8mb4 COMMENT = '套餐菜品关系';

DROP TABLE IF EXISTS `shopping_cart`;
CREATE TABLE `shopping_cart` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) DEFAULT NULL COMMENT '商品名称',
  `image` varchar(255) DEFAULT NULL COMMENT '图片',
  `user_id` bigint(20) NOT NULL COMMENT '主键',
  `dish_id` bigint(20) DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint(20) DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) DEFAULT NULL COMMENT '口味',
  `number` int(11) NOT NULL DEFAULT 1 COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 40 DEFAULT CHARSET = utf8mb4 COMMENT = '购物车';

-- 用户表：账号密码 + 角色（1用户 2商家），去掉 openid
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(32) NOT NULL COMMENT '用户名',
  `password` varchar(64) NOT NULL COMMENT '密码(MD5)',
  `name` varchar(32) DEFAULT NULL COMMENT '姓名/昵称',
  `phone` varchar(11) DEFAULT NULL COMMENT '手机号',
  `sex` varchar(2) DEFAULT NULL COMMENT '性别',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像',
  `role` int(11) NOT NULL DEFAULT 1 COMMENT '角色 1用户 2商家',
  `status` int(11) NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
  `create_time` datetime DEFAULT NULL COMMENT '注册时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_username` (`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 DEFAULT CHARSET = utf8mb4 COMMENT = '用户信息（网页版账号体系）';

SET FOREIGN_KEY_CHECKS = 1;

-- ==================== 演示数据 ====================

-- 账号（密码均为 123456 的 MD5：e10adc3949ba59abbe56e057f20f883e）
-- 用户：zhangsan / lisi；商家：merchant01 / merchant02
INSERT INTO `user` (`id`, `username`, `password`, `name`, `phone`, `sex`, `avatar`, `role`, `status`, `create_time`) VALUES
(1, 'zhangsan', 'e10adc3949ba59abbe56e057f20f883e', '张三', '13800138001', '1', '', 1, 1, '2026-08-01 10:00:00'),
(2, 'lisi',     'e10adc3949ba59abbe56e057f20f883e', '李四', '13800138002', '0', '', 1, 1, '2026-08-02 10:00:00'),
(3, 'merchant01', 'e10adc3949ba59abbe56e057f20f883e', '王老板', '13900139001', '1', '', 2, 1, '2026-08-01 10:00:00'),
(4, 'merchant02', 'e10adc3949ba59abbe56e057f20f883e', '赵老板', '13900139002', '0', '', 2, 1, '2026-08-02 10:00:00');

-- 地址簿
INSERT INTO `address_book` (`id`, `user_id`, `consignee`, `sex`, `phone`, `province_name`, `city_name`, `district_name`, `detail`, `label`, `is_default`) VALUES
(1, 1, '张三', '1', '13800138001', '北京市', '北京市', '朝阳区', '新街大道一号楼8层801', '公司', 1),
(2, 1, '张三', '1', '13800138001', '北京市', '北京市', '海淀区', '中关村大街18号', '家', 0),
(3, 2, '李四', '0', '13800138002', '上海市', '上海市', '浦东新区', '世纪大道100号', '家', 1);

-- 分类
INSERT INTO `category` (`id`, `type`, `name`, `sort`, `status`, `create_time`, `update_time`) VALUES
(1, 1, '热销榜', 1, 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(2, 1, '美味主食', 2, 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(3, 1, '可口汤粥', 3, 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(4, 1, '风味小吃', 4, 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(5, 2, '人气套餐', 5, 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00');

-- 菜品（图片使用本机占位图）
INSERT INTO `dish` (`id`, `name`, `category_id`, `price`, `image`, `description`, `status`, `create_time`, `update_time`) VALUES
(1, '宫保鸡丁', 1, 28.00, '/images/dish/chicken.svg', '经典川菜，鸡肉鲜嫩，花生香脆', 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(2, '鱼香肉丝', 1, 26.00, '/images/dish/pork.svg', '咸甜酸辣兼备，下饭神器', 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(3, '麻婆豆腐', 1, 18.00, '/images/dish/tofu.svg', '麻辣鲜香，豆腐嫩滑', 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(4, '西红柿炒蛋', 2, 16.00, '/images/dish/tomato.svg', '家常味道，酸甜可口', 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(5, '扬州炒饭', 2, 20.00, '/images/dish/friedrice.svg', '粒粒分明，配料丰富', 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(6, '牛肉拉面', 2, 22.00, '/images/dish/noodle.svg', '手工拉面，汤浓肉香', 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(7, '皮蛋瘦肉粥', 3, 12.00, '/images/dish/congee.svg', '绵软顺滑，暖胃养生', 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(8, '紫菜蛋花汤', 3, 10.00, '/images/dish/soup.svg', '清淡鲜美', 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(9, '香辣鸡翅', 4, 15.00, '/images/dish/wings.svg', '外酥里嫩，香辣过瘾', 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(10, '黄金薯条', 4, 9.00, '/images/dish/fries.svg', '金黄酥脆', 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00');

-- 菜品口味
INSERT INTO `dish_flavor` (`id`, `dish_id`, `name`, `value`) VALUES
(1, 1, '辣度', '不辣,微辣,中辣,重辣'),
(2, 2, '辣度', '不辣,微辣,中辣'),
(3, 3, '辣度', '微辣,中辣,重辣'),
(4, 6, '面量', '标准,加量'),
(5, 9, '辣度', '不辣,微辣,香辣');

-- 套餐
INSERT INTO `setmeal` (`id`, `category_id`, `name`, `price`, `status`, `description`, `image`, `create_time`, `update_time`) VALUES
(1, 5, '单人商务餐', 35.00, 1, '宫保鸡丁+米饭+紫菜蛋花汤，营养均衡', '/images/dish/setmeal1.svg', '2026-08-01 09:00:00', '2026-08-01 09:00:00'),
(2, 5, '双人分享餐', 66.00, 1, '鱼香肉丝+麻婆豆腐+米饭x2+蛋花汤', '/images/dish/setmeal2.svg', '2026-08-01 09:00:00', '2026-08-01 09:00:00');

-- 套餐明细
INSERT INTO `setmeal_dish` (`id`, `setmeal_id`, `dish_id`, `name`, `price`, `copies`) VALUES
(1, 1, 1, '宫保鸡丁', 28.00, 1),
(2, 1, 8, '紫菜蛋花汤', 10.00, 1),
(3, 2, 2, '鱼香肉丝', 26.00, 1),
(4, 2, 3, '麻婆豆腐', 18.00, 1),
(5, 2, 8, '紫菜蛋花汤', 10.00, 2);

-- 管理端员工账号（admin / 123456），供原有管理后台使用
INSERT INTO `employee` (`id`, `name`, `username`, `password`, `phone`, `sex`, `id_number`, `status`, `create_time`, `update_time`) VALUES
(1, '管理员', 'admin', 'e10adc3949ba59abbe56e057f20f883e', '13800000000', '1', '110101199001011234', 1, '2026-08-01 09:00:00', '2026-08-01 09:00:00');
CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT 'conversation user id',
  `sender_type` tinyint NOT NULL COMMENT '1 user 2 merchant',
  `sender_id` bigint NOT NULL DEFAULT 0 COMMENT 'sender id',
  `msg_type` tinyint NOT NULL DEFAULT 1 COMMENT '1 text 2 order card',
  `content` text,
  `order_id` bigint DEFAULT NULL,
  `is_read` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`,`id`),
  KEY `idx_unread_user` (`user_id`,`sender_type`,`is_read`),
  KEY `idx_unread_merchant` (`sender_type`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user merchant chat';
