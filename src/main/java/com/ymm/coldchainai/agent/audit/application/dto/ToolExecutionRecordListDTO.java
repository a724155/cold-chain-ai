package com.ymm.coldchainai.agent.audit.application.dto;

import com.ymm.coldchainai.agent.audit.domain.model.ToolExecution;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 一次Agent请求对应的Tool执行审计列表DTO。
 *
 * <p>一个requestId可能没有调用Tool，也可能调用一个或者多个Tool。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class ToolExecutionRecordListDTO {

    /**
     * 当前查询的Agent requestId。
     */
    private final String requestId;

    /**
     * 本次实际返回的Tool执行记录数量。
     */
    private final Integer returnedExecutionCount;

    /**
     * Tool执行审计记录列表。
     */
    private final List<ToolExecutionRecordDTO> toolExecutionRecordList;

    /**
     * 将Tool执行领域对象列表转换成Application DTO。
     *
     * @param requestId         Agent请求唯一标识
     * @param toolExecutionList Tool执行领域对象列表
     * @return Tool执行审计列表DTO
     */
    public static ToolExecutionRecordListDTO fromDomainList(String requestId, List<ToolExecution> toolExecutionList) {
        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("Tool审计列表requestId不能为空");
        }

        // Repository异常返回null时按没有Tool调用处理。
        List<ToolExecution> safeToolExecutionList = ListUtils.emptyIfNull(toolExecutionList);

        // 一次Stream遍历完成列表元素判空和DTO转换。Stream.toList()是JDK 16新增API，返回不可修改List。
        List<ToolExecutionRecordDTO> toolExecutionRecordDTOList = safeToolExecutionList.stream()
                .map(toolExecution -> {
                    // 空元素属于Repository返回异常，不能静默跳过。
                    if (Objects.isNull(toolExecution)) {
                        throw new IllegalArgumentException("Tool执行审计列表不能包含空元素");
                    }
                    // 每条领域对象统一转换为只包含安全摘要的查询DTO。
                    return ToolExecutionRecordDTO.fromDomain(toolExecution);
                })
                .toList();

        return ToolExecutionRecordListDTO.of(StringUtils.trim(requestId), toolExecutionRecordDTOList.size(), toolExecutionRecordDTOList);
    }
}
