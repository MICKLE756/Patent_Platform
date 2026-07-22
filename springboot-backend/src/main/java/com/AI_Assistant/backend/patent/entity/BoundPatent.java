package com.AI_Assistant.backend.patent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已绑定专利扩展表实体
 * 记录科研团队绑定的专利及统计数据
 */
@Data
@TableName("bound_patent")
public class BoundPatent {

    /**
     * 记录ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 专利ID（外部专利库/Milvus）
     */
    @TableField("patent_id")
    private String patentId;

    /**
     * 发明人
     */
    @TableField("inventor")
    private String inventor;

    /**
     * 专利名称
     */
    @TableField("patent_name")
    private String patentName;

    /**
     * 绑定用户ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 绑定用户类型: enterprise/research_team
     */
    @TableField("user_type")
    private String userType;

    /**
     * 绑定时间
     */
    @TableField("bind_time")
    private LocalDateTime bindTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}