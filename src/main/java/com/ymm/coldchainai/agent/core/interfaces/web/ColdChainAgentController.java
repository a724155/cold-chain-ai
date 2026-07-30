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
 * 冷运Agent正式问答接口。
 *
 * <p>该Controller是正式Agent Core的HTTP入口，
 * 只负责请求接收、对象转换、Application Service调用和响应封装。</p>
 *
 * <p>Controller不直接创建Conversation、不保存ChatMessage、
 * 不操作ChatClient、不注册Tool，也不实现订单或者支付业务规则。</p>
 *
 * <p><strong>前后端协议提醒：</strong>
 * 开发前必须与产品和前端确认conversationId为空时创建新会话、
 * 非空时继续原会话的规则，并明确Agent切换、会话关闭、
 * 请求重试和失败消息展示方式。</p>
 *
 * <p>在挖矿流程中，该Controller相当于矿场接待窗口：
 * 它只接收客户提交的项目编号和作业要求，
 * 再把标准任务单交给项目总调度员，不亲自进入矿区作业。</p>
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
        AgentChatCommand command = AgentChatCommand.of(request.getConversationId(), request.getAgentCode(), request.getQuestion());

        // 调用Application Service完成Agent问答用例编排。
        AgentAnswerDTO agentAnswerDTO = coldChainAgentApplicationService.chat(command);

        // 将Application DTO转换成HTTP Response，保持接口对象边界清晰。
        AgentChatResponse response = AgentChatResponse.of(agentAnswerDTO.getRequestId(), agentAnswerDTO.getConversationId(),
                agentAnswerDTO.getAgentCode(), agentAnswerDTO.getAgentName(),
                agentAnswerDTO.getAnswer(), agentAnswerDTO.getCostMillis());

        return YmmResult.success(response);
    }
}
