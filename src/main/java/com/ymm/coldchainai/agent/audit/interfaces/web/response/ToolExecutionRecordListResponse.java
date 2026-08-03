package com.ymm.coldchainai.agent.audit.interfaces.web.response;

import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionRecordDTO;
import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionRecordListDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.ListUtils;

import java.util.List;
import java.util.Objects;

/**
 * 一次Agent请求对应的Tool执行审计列表响应。
 */
@Getter
@AllArgsConstructor
public class ToolExecutionRecordListResponse {

    /**
     * 当前查询的Agent requestId。
     */
    private final String requestId;

    /**
     * 本次返回的Tool执行记录数量。
     */
    private final Integer returnedExecutionCount;

    /**
     * Tool执行审计响应列表。
     */
    private final List<ToolExecutionRecordResponse> toolExecutionRecordList;

    /**
     * 将Application列表DTO转换成HTTP响应。
     *
     * @param recordListDTO Tool审计列表DTO
     * @return Tool审计列表响应
     */
    public static ToolExecutionRecordListResponse fromDTO(ToolExecutionRecordListDTO recordListDTO) {

        if (Objects.isNull(recordListDTO)) {
            throw new IllegalArgumentException("Tool执行审计列表DTO不能为空");
        }

        // DTO列表为null时按没有Tool调用处理。
        List<ToolExecutionRecordDTO> recordDTOList = ListUtils.emptyIfNull(recordListDTO.getToolExecutionRecordList());

        // 单次遍历完成空元素校验和HTTP Response转换。
        List<ToolExecutionRecordResponse> recordResponseList = recordDTOList.stream()
                .map(recordDTO -> {
                    if (Objects.isNull(recordDTO)) {
                        throw new IllegalArgumentException("Tool执行审计DTO列表不能包含空元素");
                    }
                    return ToolExecutionRecordResponse.fromDTO(recordDTO);
                })
                .toList();

        return new ToolExecutionRecordListResponse(recordListDTO.getRequestId(), recordResponseList.size(), recordResponseList);
    }
}