package com.ymm.coldchainai.agent.scenario.knowledge.interfaces.tool.response;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchDTO;
import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchItemDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.ListUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 内部规范知识查询Tool响应。
 *
 * <p>该响应是知识RAG模块与Agent之间的稳定边界，
 * Agent只看到已经检索完成的知识原文，不直接依赖PGVector、EmbeddingModel或者Spring AI Document。</p>
 *
 * <p>Tool返回的是“资料”，而不是提前生成好的最终答案。
 * 最终回答仍然由cold-chain-general模型结合用户原始问题和这些可信资料完成。</p>
 */
@Getter
@AllArgsConstructor
public class InternalRuleKnowledgeQueryToolResponse {

    /**
     * 实际执行知识检索的问题。
     */
    private final String query;

    /**
     * 当前使用的知识文档编码。
     */
    private final String documentCode;

    /**
     * 当前使用的知识文档版本。
     */
    private final String documentVersion;

    /**
     * 本次实际召回的知识Chunk数量。
     */
    private final Integer resultCount;

    /**
     * 按相似度从高到低排列的知识片段列表。
     */
    private final List<InternalRuleKnowledgeQueryToolItemResponse> resultItemList;

    /**
     * 将Application层检索结果转换成Agent Tool响应。
     *
     * <p>方法自身负责DTO、List以及List元素的完整空值保护，
     * 避免未来其他Agent场景复用该转换方法时产生隐藏NPE。</p>
     *
     * @param searchDTO Application层知识检索结果
     * @return Tool响应
     */
    public static InternalRuleKnowledgeQueryToolResponse fromDTO(InternalRuleKnowledgeSearchDTO searchDTO) {
        if (Objects.isNull(searchDTO)) {
            throw new IllegalArgumentException("内部规范知识查询Tool检索DTO不能为空");
        }

        // 将null List安全转换为空List，避免直接stream导致空指针。
        List<InternalRuleKnowledgeSearchItemDTO> searchItemDTOList = ListUtils.emptyIfNull(searchDTO.getResultItemList());

        // 根据实际召回数量预分配容量，避免转换过程中ArrayList重复扩容。
        List<InternalRuleKnowledgeQueryToolItemResponse> resultItemList = new ArrayList<>(searchItemDTOList.size());

        // 每个元素都必须合法，不能通过filter静默丢弃异常数据。
        for (int index = 0; index < searchItemDTOList.size(); index++) {
            InternalRuleKnowledgeSearchItemDTO itemDTO = searchItemDTOList.get(index);

            if (Objects.isNull(itemDTO)) {
                throw new IllegalArgumentException("内部规范知识查询Tool结果元素不能为空，index=%s".formatted(index));
            }

            resultItemList.add(InternalRuleKnowledgeQueryToolItemResponse.fromDTO(itemDTO));
        }

        // documentCode和documentVersion从实际召回结果中获取。当前内部规范检索已经通过Metadata Filter保证所有结果来自同一文档和同一版本。
        String documentCode = resultItemList.isEmpty() ? null : searchItemDTOList.getFirst().getDocumentCode();
        String documentVersion = resultItemList.isEmpty() ? null : searchItemDTOList.getFirst().getDocumentVersion();

        return new InternalRuleKnowledgeQueryToolResponse(
                searchDTO.getQuery(),
                documentCode,
                documentVersion,
                resultItemList.size(),
                List.copyOf(resultItemList));
    }
}
