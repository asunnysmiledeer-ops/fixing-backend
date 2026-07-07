package com.fixing.web.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fixing.auth.RequirePerm;
import com.fixing.common.Result;
import com.fixing.user.domain.SysRolePerm;
import com.fixing.user.domain.UserRole;
import com.fixing.user.mapper.SysRolePermMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 平台·权限管理：角色×权限矩阵的可视化编辑。
 * 这就是"改权限=改数据库"的界面化 —— 勾/去勾一个格子，对应角色的
 * 页签和接口权限**即刻**变化（权限每次请求实时查表）。
 */
@RestController
@RequestMapping("/platform/perms")
public class PlatformPermController {

    private final SysRolePermMapper rolePermMapper;

    public PlatformPermController(SysRolePermMapper rolePermMapper) {
        this.rolePermMapper = rolePermMapper;
    }

    /** 矩阵数据：全部权限字符串（去重） + 每个角色当前拥有的 */
    @RequirePerm("platform:perm:list")
    @GetMapping("/matrix")
    public Result<Map<String, Object>> matrix() {
        List<SysRolePerm> all = rolePermMapper.selectList(null);
        List<String> perms = all.stream().map(SysRolePerm::getPerm).distinct().sorted().toList();
        Map<String, List<String>> byRole = new java.util.LinkedHashMap<>();
        for (UserRole role : new UserRole[]{UserRole.ADMIN, UserRole.ENGINEER, UserRole.CUSTOMER}) {
            byRole.put(role.name(), all.stream()
                    .filter(rp -> rp.getRole() == role).map(SysRolePerm::getPerm).toList());
        }
        return Result.ok(Map.of("perms", perms, "byRole", byRole));
    }

    /** 授予：给角色加一条权限（幂等） */
    @RequirePerm("platform:perm:edit")
    @PostMapping("/grant")
    public Result<Void> grant(@RequestBody Map<String, String> body) {
        UserRole role = UserRole.valueOf(body.get("role"));
        String perm = body.get("perm");
        boolean exists = rolePermMapper.selectCount(new LambdaQueryWrapper<SysRolePerm>()
                .eq(SysRolePerm::getRole, role).eq(SysRolePerm::getPerm, perm)) > 0;
        if (!exists) {
            SysRolePerm rp = new SysRolePerm();
            rp.setRole(role);
            rp.setPerm(perm);
            rolePermMapper.insert(rp);
        }
        return Result.ok();
    }

    /** 收回：删掉角色的一条权限 */
    @RequirePerm("platform:perm:edit")
    @PostMapping("/revoke")
    public Result<Void> revoke(@RequestBody Map<String, String> body) {
        rolePermMapper.delete(new LambdaQueryWrapper<SysRolePerm>()
                .eq(SysRolePerm::getRole, UserRole.valueOf(body.get("role")))
                .eq(SysRolePerm::getPerm, body.get("perm")));
        return Result.ok();
    }
}
