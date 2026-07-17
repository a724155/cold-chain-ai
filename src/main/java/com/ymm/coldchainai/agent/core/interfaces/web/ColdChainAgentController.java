package com.ymm.coldchainai.agent.core.interfaces.web;

import com.ymm.coldchainai.agent.core.application.command.AgentChatCommand;
import com.ymm.coldchainai.agent.core.application.dto.AgentAnswerDTO;
import com.ymm.coldchainai.agent.core.application.service.IColdChainAgentApplicationService;
import com.ymm.coldchainai.agent.core.interfaces.web.request.AgentChatRequest;
import com.ymm.coldchainai.agent.core.interfaces.web.response.AgentChatResponse;
import com.ymm.coldchainai.shared.response.YmmResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 冷运 Agent 正式问答接口。
 *
 * <p>该 Controller 是正式 Agent Core 的 HTTP 入口。
 * 它只负责请求接收、对象转换、Application Service 调用和响应封装。</p>
 *
 * <p>Controller 不直接操作 ChatClient、不注册 Tool、
 * 不实现订单或支付业务规则。</p>
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ColdChainAgentController {

    /**
     * 冷运 Agent 应用服务。
     */
    private final IColdChainAgentApplicationService coldChainAgentApplicationService;

    /**
     * 执行一次正式冷运 Agent 问答。
     *
     * @param request Agent HTTP请求
     * @return 包含requestId、答案和耗时的统一成功结果
     */
    @PostMapping("/chat")
    public YmmResult<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest request) {
        // 将HTTP请求对象转换成Application层命令，避免Request对象穿透到业务层。
        AgentChatCommand command = AgentChatCommand.of(request.getQuestion());

        // 调用Application Service完成Agent问答用例编排。
        AgentAnswerDTO agentAnswerDTO = coldChainAgentApplicationService.chat(command);

        // 将Application DTO转换成HTTP Response，保持接口对象边界清晰。
        AgentChatResponse response = AgentChatResponse.of(agentAnswerDTO.getRequestId(), agentAnswerDTO.getAnswer(), agentAnswerDTO.getCostMillis());

        return YmmResult.success(response);
    }
}
