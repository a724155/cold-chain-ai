package com.ymm.coldchainai.agent.core.application.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 正式Agent问答用例命令。
 *
 * <p>Controller接收到AgentChatRequest后，需要转换成该Command，
 * Application Service不直接依赖HTTP请求对象。</p>
 *
 * <p>conversationId负责表达“创建新会话”或者“继续原会话”，
 * 但用户和租户身份不会进入该Command，
 * Application Service仍然从ICurrentUserContext读取受信任身份。</p>
 *
 * <p><strong>需求确认提醒：</strong>
 * Command字段应来源于已经确认过的PRD和接口协议。
 * 如果产品没有明确新建会话、继续会话、Agent切换和关闭会话规则，
 * 应先补齐需求确认，避免Application层围绕错误假设开发。</p>
 *
 * <p>在挖矿流程中，该Command相当于接待窗口整理后的标准项目任务单：
 * conversationId表示原项目编号，agentCode表示负责矿区，
 * question表示本轮具体作业内容。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentChatCommand {

    /**
     * 已有Conversation业务唯一标识。
     *
     * <p>为空表示创建新Conversation，非空表示继续已有Conversation。</p>
     */
    private final String conversationId;

    /**
     * 调用方指定的Agent编码，可以为空。
     */
    private final String agentCode;

    /**
     * 用户本轮提交给Agent的问题。
     */
    private final String question;
}