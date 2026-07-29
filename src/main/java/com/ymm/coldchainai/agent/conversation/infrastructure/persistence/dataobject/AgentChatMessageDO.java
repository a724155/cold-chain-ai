package com.ymm.coldchainai.agent.conversation.infrastructure.persistence.dataobject;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Agent聊天消息数据库持久化对象。
 *
 * <p>该对象与cold_chain_ai_chat_message表一一对应，
 * 只负责在Java对象和MySQL记录之间搬运数据，不承载消息顺序计算、
 * 会话状态校验、用户权限校验等业务行为。</p>
 *
 * <p>AgentChatMessage是具备创建和恢复规则的领域模型，
 * AgentChatMessageDO则是MyBatis认识的数据库档案对象，二者不能混用。</p>
 *
 * <p>在挖矿流程中，AgentChatMessage相当于业务人员理解的单条作业记录，
 * AgentChatMessageDO相当于档案仓库按照固定表格格式保存的纸质档案。</p>
 */
@Getter
@Setter
public class AgentChatMessageDO {

    /**
     * 数据库内部自增主键。
     *
     * <p>该字段只用于MySQL内部索引和数据关联，
     * 不作为前后端交互使用的消息业务标识。</p>
     */
    private Long id;

    /**
     * 消息业务唯一标识。
     *
     * <p>该字段对外用于日志定位、审计和业务查询，
     * 对应数据库message_id字段。</p>
     */
    private String messageId;

    /**
     * 当前消息所属Conversation业务唯一标识。
     *
     * <p>同一conversationId下可以保存多条USER和ASSISTANT消息，
     * 对应数据库conversation_id字段。</p>
     */
    private String conversationId;

    /**
     * 当前消息所属用户ID。
     *
     * <p>该字段来自受信任认证上下文，不接受前端直接指定，
     * 查询Chat History时必须与conversationId、currentTenantId共同作为权限条件。</p>
     */
    private Long currentUserId;

    /**
     * 当前消息所属租户ID。
     *
     * <p>用于多租户数据隔离，避免不同租户之间读取相同conversationId下的消息。</p>
     */
    private Long currentTenantId;

    /**
     * 产生当前消息的Agent请求唯一标识。
     *
     * <p>同一轮USER问题和ASSISTANT回答使用同一个requestId，
     * 便于后续根据一次Agent执行还原完整问答链路。</p>
     */
    private String requestId;

    /**
     * 消息角色数据库编码。
     *
     * <p>当前约定：1表示USER，2表示ASSISTANT。
     * 持久化层保存Integer，恢复领域模型时通过ChatMessageRoleEnum.fromCode()转换。</p>
     */
    private Integer messageRole;

    /**
     * 聊天消息完整正文。
     *
     * <p>USER消息保存用户实际提交的问题，
     * ASSISTANT消息保存最终返回给用户的自然语言答案。</p>
     */
    private String messageContent;

    /**
     * 当前消息在Conversation中的顺序号。
     *
     * <p>sequenceNo从1开始递增，用于恢复真实聊天顺序。
     * 该值不能仅依赖当前消息表MAX查询临时计算，
     * 后续通过锁定Conversation并基于messageCount生成，避免并发重复。</p>
     */
    private Integer sequenceNo;

    /**
     * 当前消息创建时间。
     *
     * <p>该时间表示消息实际持久化产生的时间，
     * 用于历史消息展示、审计和后续Chat Memory加载排序。</p>
     */
    private LocalDateTime createTime;
}
