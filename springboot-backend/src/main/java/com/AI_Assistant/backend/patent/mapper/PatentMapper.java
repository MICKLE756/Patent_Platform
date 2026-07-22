package com.AI_Assistant.backend.patent.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.AI_Assistant.backend.patent.entity.Patent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 专利Mapper接口
 * 从patent表查询专利数据
 */
@Mapper
public interface PatentMapper extends BaseMapper<Patent> {

    /**
     * 根据专利ID查询专利信息
     *
     * @param patentId 专利ID
     * @return 专利信息
     */
    @Select("SELECT * FROM patent WHERE patent_id = #{patentId}")
    Patent selectByPatentId(@Param("patentId") String patentId);

    /**
     * 查询所有专利
     *
     * @return 专利列表
     * @deprecated 该方法会查询全部专利，数据量大时可能导致内存溢出，建议使用分页查询
     */
    // @Select("SELECT * FROM patent")
    // List<Patent> selectAll();

    /**
     * 根据关键词搜索专利（标题、摘要、发明人、申请人、技术领域）
     *
     * @param keyword 关键词
     * @return 专利列表
     */
    @Select("SELECT * FROM patent WHERE " +
            "title LIKE CONCAT('%', #{keyword}, '%') OR " +
            "abstract LIKE CONCAT('%', #{keyword}, '%') OR " +
            "inventors LIKE CONCAT('%', #{keyword}, '%') OR " +
            "applicant LIKE CONCAT('%', #{keyword}, '%') OR " +
            "tech_field LIKE CONCAT('%', #{keyword}, '%')")
    List<Patent> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 根据专利类型筛选专利
     *
     * @param patentType 专利类型
     * @return 专利列表
     */
    @Select("SELECT * FROM patent WHERE patent_type = #{patentType}")
    List<Patent> selectByPatentType(@Param("patentType") String patentType);

    /**
     * 根据法律状态筛选专利
     *
     * @param legalStatus 法律状态
     * @return 专利列表
     */
    @Select("SELECT * FROM patent WHERE legal_status = #{legalStatus}")
    List<Patent> selectByLegalStatus(@Param("legalStatus") String legalStatus);

    /**
     * 根据有效性筛选专利
     *
     * @param validity 有效性
     * @return 专利列表
     */
    @Select("SELECT * FROM patent WHERE validity = #{validity}")
    List<Patent> selectByValidity(@Param("validity") String validity);

    /**
     * 综合条件查询专利（支持多条件组合）
     *
     * @param keyword     关键词（可选）
     * @param patentType  专利类型（可选）
     * @param legalStatus 法律状态（可选）
     * @param validity    有效性（可选）
     * @return 专利列表
     */
    @Select("<script>" +
            "SELECT * FROM patent WHERE 1=1" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (title LIKE CONCAT('%', #{keyword}, '%') OR " +
            "abstract LIKE CONCAT('%', #{keyword}, '%') OR " +
            "inventors LIKE CONCAT('%', #{keyword}, '%') OR " +
            "applicant LIKE CONCAT('%', #{keyword}, '%') OR " +
            "tech_field LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "<if test='patentType != null and patentType != \"\"'>" +
            "AND patent_type = #{patentType}" +
            "</if>" +
            "<if test='legalStatus != null and legalStatus != \"\"'>" +
            "AND legal_status = #{legalStatus}" +
            "</if>" +
            "<if test='validity != null and validity != \"\"'>" +
            "AND validity = #{validity}" +
            "</if>" +
            "</script>")
    List<Patent> selectByConditions(
            @Param("keyword") String keyword,
            @Param("patentType") String patentType,
            @Param("legalStatus") String legalStatus,
            @Param("validity") String validity
    );

    /**
     * 综合条件查询专利总数
     *
     * @param keyword     关键词（可选）
     * @param patentType  专利类型（可选）
     * @param legalStatus 法律状态（可选）
     * @param validity    有效性（可选）
     * @return 专利总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM patent WHERE 1=1" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (title LIKE CONCAT('%', #{keyword}, '%') OR " +
            "abstract LIKE CONCAT('%', #{keyword}, '%') OR " +
            "inventors LIKE CONCAT('%', #{keyword}, '%') OR " +
            "applicant LIKE CONCAT('%', #{keyword}, '%') OR " +
            "tech_field LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "<if test='patentType != null and patentType != \"\"'>" +
            "AND patent_type = #{patentType}" +
            "</if>" +
            "<if test='legalStatus != null and legalStatus != \"\"'>" +
            "AND legal_status = #{legalStatus}" +
            "</if>" +
            "<if test='validity != null and validity != \"\"'>" +
            "AND validity = #{validity}" +
            "</if>" +
            "</script>")
    int countByConditions(
            @Param("keyword") String keyword,
            @Param("patentType") String patentType,
            @Param("legalStatus") String legalStatus,
            @Param("validity") String validity
    );

    /**
     * 统计专利总数
     *
     * @return 专利总数
     */
    @Select("SELECT COUNT(*) FROM patent")
    int countAll();

    /**
     * 根据发明人查询专利
     *
     * @param inventor 发明人
     * @return 专利列表
     */
    @Select("SELECT * FROM patent WHERE inventors LIKE CONCAT('%', #{inventor}, '%')")
    List<Patent> selectByInventor(@Param("inventor") String inventor);

    /**
     * 根据申请人查询专利
     *
     * @param applicant 申请人
     * @return 专利列表
     */
    @Select("SELECT * FROM patent WHERE applicant = #{applicant}")
    List<Patent> selectByApplicant(@Param("applicant") String applicant);

    /**
     * 根据发明人和申请人查询专利（OR条件）
     *
     * @param inventor  发明人
     * @param applicant 申请人
     * @return 专利列表
     */
    @Select("SELECT * FROM patent WHERE " +
            "inventors LIKE CONCAT('%', #{inventor}, '%') OR applicant = #{applicant}")
    List<Patent> selectByInventorOrApplicant(
            @Param("inventor") String inventor,
            @Param("applicant") String applicant
    );

    /**
     * 综合条件分页查询专利（使用MyBatis-Plus分页）
     *
     * @param page        分页参数
     * @param keyword     关键词（可选）
     * @param patentType  专利类型（可选）
     * @param legalStatus 法律状态（可选）
     * @param validity    有效性（可选）
     * @return 分页专利列表
     */
    @Select("<script>" +
            "SELECT * FROM patent WHERE 1=1" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (title LIKE CONCAT('%', #{keyword}, '%') OR " +
            "abstract LIKE CONCAT('%', #{keyword}, '%') OR " +
            "inventors LIKE CONCAT('%', #{keyword}, '%') OR " +
            "applicant LIKE CONCAT('%', #{keyword}, '%') OR " +
            "tech_field LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "<if test='patentType != null and patentType != \"\"'>" +
            "AND patent_type = #{patentType}" +
            "</if>" +
            "<if test='legalStatus != null and legalStatus != \"\"'>" +
            "AND legal_status = #{legalStatus}" +
            "</if>" +
            "<if test='validity != null and validity != \"\"'>" +
            "AND validity = #{validity}" +
            "</if>" +
            "</script>")
    IPage<Patent> selectByConditionsPage(
            IPage<Patent> page,
            @Param("keyword") String keyword,
            @Param("patentType") String patentType,
            @Param("legalStatus") String legalStatus,
            @Param("validity") String validity
    );

    /**
     * 根据发明人和申请人分页查询专利（数据库层精确匹配）
     * 使用正则表达式精确匹配发明人，避免模糊匹配误匹配
     *
     * @param page      分页参数
     * @param inventor  发明人（团队名称）
     * @param applicant 申请人（所属单位）
     * @return 分页专利列表
     */
    @Select("<script>" +
            "SELECT * FROM patent WHERE 1=1" +
            "<if test='inventor != null and inventor != \"\"'>" +
            "AND (inventors REGEXP CONCAT('(^|；)', #{inventor}, '(；|$)') OR applicant = #{applicant})" +
            "</if>" +
            "<if test='inventor == null or inventor == \"\"'>" +
            "AND applicant = #{applicant}" +
            "</if>" +
            "</script>")
    IPage<Patent> selectPatentsByInventorOrApplicant(
            IPage<Patent> page,
            @Param("inventor") String inventor,
            @Param("applicant") String applicant
    );

    // ==================== 使用 XML ResultMap 处理 TEXT 类型字段的方法 ====================

    /**
     * 根据专利ID查询专利信息（使用XML ResultMap处理TEXT类型）
     * 解决 abstract 字段（TEXT类型）无法正确映射的问题
     *
     * @param patentId 专利ID
     * @return 专利信息
     */
    Patent selectByPatentIdResultMap(@Param("patentId") String patentId);

    /**
     * 查询所有专利（使用XML ResultMap处理TEXT类型）
     *
     * @return 专利列表
     */
    List<Patent> selectAllResultMap();

    /**
     * 根据专利ID列表批量查询专利（使用XML ResultMap处理TEXT类型）
     *
     * @param patentIds 专利ID列表
     * @return 专利列表
     */
    List<Patent> selectBatchPatentsByIdsResultMap(@Param("patentIds") List<String> patentIds);
}
