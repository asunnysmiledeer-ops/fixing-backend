package com.fixing.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体，对应表 sys_user。
 *
 * <p>v0 不做登录认证（JWT/Session 都不上），调用接口时直接传 operatorId，
 * Service 拿它查出用户再做角色校验。真实登录留给 v1（M1 完整版）。
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名，唯一 */
    private String username;

    /** 密码。v0 用明文占位；上线前必须换成 BCrypt 散列 */
    private String password;

    /** 角色：CUSTOMER / ADMIN / ENGINEER（MyBatis 默认按枚举名存取 VARCHAR 列） */
    private UserRole role;

    /** 真实姓名，展示用 */
    private String realName;

    private LocalDateTime createdAt;
}
