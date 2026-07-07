package com.fixing.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限映射，对应表 sys_role_perm。
 * 一行 = "某角色拥有某权限字符串"。改权限 = 改这张表，不改代码。
 * （v0.5 做管理界面后运营可自助配置；M1 先由种子数据初始化。）
 */
@Data
@TableName("sys_role_perm")
public class SysRolePerm {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色：CUSTOMER / ADMIN / ENGINEER（枚举名存字符串） */
    private UserRole role;

    /** 权限字符串，如 maint:ticket:assign */
    private String perm;
}
