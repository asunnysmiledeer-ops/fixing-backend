package com.fixing.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.user.domain.SysRolePerm;
import com.fixing.user.domain.UserRole;
import com.fixing.user.mapper.SysRolePermMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限服务：角色 → 权限字符串列表。
 *
 * <p>规则：
 * - ADMIN 短路放行（平台管理员天然全权限，和若依内置 admin 一致）；
 *   permsOf(ADMIN) 返回表里全部去重权限 —— 前端要靠列表画页签。
 * - 其他角色查 sys_role_perm 精确匹配。
 *
 * <p>Demo 数据量下每次查库（一条索引查询），上量后加本地缓存 + 改表时失效。
 */
@Service
public class PermissionService {

    private final SysRolePermMapper rolePermMapper;

    public PermissionService(SysRolePermMapper rolePermMapper) {
        this.rolePermMapper = rolePermMapper;
    }

    /** 某角色的全部权限（登录响应下发给前端，驱动页签/按钮渲染） */
    public List<String> permsOf(UserRole role) {
        if (role == UserRole.SUPER_ADMIN) {
            // 超管 = 全量权限（去重）。selectObjs 只取单列，避免整行装配
            return rolePermMapper.selectObjs(new LambdaQueryWrapper<SysRolePerm>()
                            .select(SysRolePerm::getPerm))
                    .stream().map(String::valueOf).distinct().toList();
        }
        return rolePermMapper.selectObjs(new LambdaQueryWrapper<SysRolePerm>()
                        .select(SysRolePerm::getPerm)
                        .eq(SysRolePerm::getRole, role))
                .stream().map(String::valueOf).toList();
    }

    /** 拦截器调用：该角色是否拥有所需权限之一 */
    public boolean hasAny(UserRole role, String[] required) {
        if (role == UserRole.SUPER_ADMIN) {
            return true; // 唯一短路角色：平台超管。ADMIN 也走表 —— 运营权限同样可配
        }
        List<String> owned = permsOf(role);
        for (String need : required) {
            if (owned.contains(need)) {
                return true;
            }
        }
        return false;
    }
}
