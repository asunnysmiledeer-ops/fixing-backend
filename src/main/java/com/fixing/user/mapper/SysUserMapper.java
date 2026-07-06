package com.fixing.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fixing.user.domain.SysUser;

/**
 * 用户 Mapper。
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper 后，selectById / insert / updateById /
 * selectList 等单表 CRUD 全部白送，一行 SQL 都不用写。
 * 接口的实现类由 MyBatis 在运行期动态生成（@MapperScan 扫到即生效）。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}
