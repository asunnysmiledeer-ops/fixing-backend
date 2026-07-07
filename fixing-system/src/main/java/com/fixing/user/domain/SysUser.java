package com.fixing.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fixing.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体，对应表 sys_user。
 * 登录链：POST /auth/login 校验 BCrypt → 签发 JWT → AuthInterceptor 放进 UserContext。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名，唯一 */
    private String username;

    /** 密码的 BCrypt 散列（永不存明文；对外接口永不返回该字段） */
    private String password;

    /** 角色：CUSTOMER / ADMIN / ENGINEER（权限字符串按角色查 sys_role_perm） */
    private UserRole role;

    /** 真实姓名，展示用 */
    private String realName;

    /** 客户角色专用：所属客户单位（数据隔离的根）；管理员/工程师为 null */
    private Long customerId;
}
