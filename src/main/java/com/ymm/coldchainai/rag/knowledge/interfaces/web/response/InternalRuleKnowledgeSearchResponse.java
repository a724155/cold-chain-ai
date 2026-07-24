package com.ymm.coldchainai.rag.knowledge.interfaces.web.response;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchDTO;
import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchItemDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.ListUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 内部规范向量检索验证响应。
 *
 * <p>当前响应故意返回Score和Chunk原文，
 * 方便研发人员判断检索排名是否准确。
 * 正式Agent接口不会把这些底层检索细节直接暴露给用户。</p>
 */
@Getter
@AllArgsConstructor
public class InternalRuleKnowledgeSearchResponse {

    /**
     * 实际检索问题。
     */
    private final String query;

    /**
     * 本次配置的最大候选数量。
     */
    private final Integer topK;

    /**
     * 本次使用的相似度阈值。
     */
    private final Double similarityThreshold;

    /**
     * 实际召回数量。
     */
    private final Integer resultCount;

    /**
     * 按相似度从高到低排列的结果。
     */
    private final List<InternalRuleKnowledgeSearchItemResponse> resultItemList;

    /**
     * 将Application检索结果转换成验证接口响应。
     *
     * <p>该方法自身承担完整防御性校验，不能依赖当前Controller或者Service一定返回合法对象。
     * 即使未来被其他调用方直接复用，也不会因为searchDTO、结果列表或者列表元素为空产生NPE。</p>
     *
     * @param searchDTO Application检索结果
     * @return HTTP接口响应
     */
    public static InternalRuleKnowledgeSearchResponse fromDTO(InternalRuleKnowledgeSearchDTO searchDTO) {

        if (Objects.isNull(searchDTO)) {
            throw new IllegalArgumentException("内部规范知识检索DTO不能为空");
        }

        // ListUtils.emptyIfNull保证Application未来异常返回null List时不会直接发生空指针。
        List<InternalRuleKnowledgeSearchItemDTO> searchItemDTOList = ListUtils.emptyIfNull(searchDTO.getResultItemList());

        // 提前指定容量，避免结果转换过程中ArrayList重复扩容。
        List<InternalRuleKnowledgeSearchItemResponse> resultItemList = new ArrayList<>(searchItemDTOList.size());

        // 对列表元素逐个校验，不能通过filter静默丢弃异常数据，否则可能掩盖上游数据问题。
        for (int index = 0; index < searchItemDTOList.size(); index++) {
            InternalRuleKnowledgeSearchItemDTO itemDTO = searchItemDTOList.get(index);

            if (Objects.isNull(itemDTO)) {
                throw new IllegalArgumentException("内部规范知识检索结果元素不能为空，index=%s".formatted(index));
            }

            resultItemList.add(InternalRuleKnowledgeSearchItemResponse.fromDTO(itemDTO));
        }

        return new InternalRuleKnowledgeSearchResponse(
                searchDTO.getQuery(),
                searchDTO.getTopK(),
                searchDTO.getSimilarityThreshold(),
                searchDTO.getResultCount(),
                resultItemList);
    }
}
