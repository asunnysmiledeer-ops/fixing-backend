package com.fixing.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体，对应表 sys_user。
 *
 * <p>v0.2 起有真实登录：POST /auth/login 校验 BCrypt 密码后签发 JWT，
 * AuthInterceptor 解析令牌把用户放进 UserContext，业务层从那里取操作人。
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名，唯一 */
    private String username;

    /** 密码的 BCrypt 散列（永不存明文；对外接口也永不返回该字段） */
    private String password;

    /** 角色：CUSTOMER / ADMIN / ENGINEER（MyBatis 默认按枚举名存取 VARCHAR 列） */
    private UserRole role;

    /** 真实姓名，展示用 */
    private String realName;

    private LocalDateTime createdAt;
}
