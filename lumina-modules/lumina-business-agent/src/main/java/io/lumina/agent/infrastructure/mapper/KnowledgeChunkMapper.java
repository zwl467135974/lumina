package io.lumina.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lumina.agent.infrastructure.entity.KnowledgeChunkDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库 chunk Mapper（全文检索）
 *
 * @author Lumina Team
 * @since 3.3.0
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkDO> {

    /**
     * MySQL FULLTEXT 全文检索（ngram 分词，支持中文）
     *
     * @param query    搜索关键词
     * @param tenantId 租户 ID
     * @param kbId     知识库 ID（null 则不限）
     * @param limit    返回条数
     * @return 匹配的 chunk 列表
     */
    @Select("""
            <script>
            SELECT * FROM lumina_knowledge_chunk
            WHERE tenant_id = #{tenantId}
            <if test="kbId != null"> AND kb_id = #{kbId} </if>
            AND MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) DESC
            LIMIT #{limit}
            </script>
            """)
    List<KnowledgeChunkDO> fulltextSearch(@Param("query") String query,
                                           @Param("tenantId") Long tenantId,
                                           @Param("kbId") Long kbId,
                                           @Param("limit") int limit);
}
