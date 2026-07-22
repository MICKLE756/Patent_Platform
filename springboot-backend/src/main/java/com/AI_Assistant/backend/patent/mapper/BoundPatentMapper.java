package com.AI_Assistant.backend.patent.mapper;

import com.AI_Assistant.backend.patent.entity.BoundPatent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 已绑定专利扩展表Mapper
 */
@Mapper
public interface BoundPatentMapper extends BaseMapper<BoundPatent> {

    /**
     * 根据发明人查询已绑定的专利ID列表
     *
     * @param inventor 发明人
     * @return 专利ID列表
     */
    @Select("SELECT patent_id FROM bound_patent WHERE inventor = #{inventor}")
    List<String> selectPatentIdsByInventor(@Param("inventor") String inventor);

    /**
     * 根据用户ID查询绑定的专利列表
     *
     * @param userId 用户ID
     * @return 绑定专利列表
     */
    @Select("SELECT * FROM bound_patent WHERE user_id = #{userId}")
    List<BoundPatent> selectByUserId(@Param("userId") String userId);

    /**
     * 根据专利ID和用户ID查询绑定记录
     *
     * @param patentId 专利ID
     * @param userId   用户ID
     * @return 绑定记录
     */
    @Select("SELECT * FROM bound_patent WHERE patent_id = #{patentId} AND user_id = #{userId}")
    BoundPatent selectByPatentIdAndUserId(@Param("patentId") String patentId, @Param("userId") String userId);

    /**
     * 根据专利ID查询绑定记录
     *
     * @param patentId 专利ID
     * @return 绑定记录列表
     */
    @Select("SELECT * FROM bound_patent WHERE patent_id = #{patentId}")
    List<BoundPatent> selectByPatentId(@Param("patentId") String patentId);
}