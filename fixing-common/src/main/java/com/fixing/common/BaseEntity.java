package com.fixing.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 所有业务实体的公共基类（参考 zzyl/若依 BaseEntity 思想）——
 * 真实项目的三件"不做会死"的事，一次性长在基类上：
 *
 * 1. 审计四件套：谁在什么时候建的/改的（出了问题能追责）；
 *    由 AuditMetaObjectHandler 自动填充，业务代码永远不用手动 set。
 * 2. tenant_id 多租户预留：现在恒为 1，将来做多租户不用改表。
 * 3. del_flag 软删除：@TableLogic 让 MyBatis-Plus 把 delete 自动改写成
 *    UPDATE del_flag='1'，查询自动追加 del_flag='0' —— 真实客户数据永不物理删除。
 */
@Data
public abstract class BaseEntity {

    /** 创建人（sys_user.user_id），插入时自动填 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间，插入时自动填 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后修改人，更新时自动填 */
    @TableField(fill = FieldFill.UPDATE)
    private Long updateBy;

    /** 最后修改时间，更新时自动填 */
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    /** 多租户预留：当前单租户恒为 1（数据库列有默认值，插入时可不管） */
    private Long tenantId;

    /** 软删除标记：0=正常 1=已删除。@TableLogic 接管增删查改的行为 */
    @TableLogic
    private String delFlag;
}
