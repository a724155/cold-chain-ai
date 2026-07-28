package com.ymm.coldchainai.bootstrap.config;

import com.ymm.coldchainai.agent.conversation.infrastructure.persistence.mapper.IAgentConversationMapper;
import com.ymm.coldchainai.agent.core.infrastructure.persistence.mapper.IAgentExecutionMapper;
import com.ymm.coldchainai.order.infrastructure.persistence.mapper.IColdChainOrderMapper;
import com.ymm.coldchainai.payment.infrastructure.persistence.mapper.IColdChainPayOrderMapper;
import com.ymm.coldchainai.verification.infrastructure.persistence.mapper.IDatabaseVerificationMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 基础配置。
 *
 * <p>该配置类负责明确指定 MyBatis Mapper 接口的扫描范围，
 * 使 Mapper 接口能够被 MyBatis 创建成代理对象并注册到 Spring 容器。</p>
 *
 * <p>当前第一阶段只扫描数据库连通性验证 Mapper。
 * 后续开发订单、支付、知识库等模块时，再按照实际存在的 Mapper 包逐步扩展扫描范围，
 * 不提前配置尚未创建的空软件包。</p>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = {
        IDatabaseVerificationMapper.class,
        IAgentExecutionMapper.class,
        IColdChainOrderMapper.class,
        IColdChainPayOrderMapper.class,
        IAgentConversationMapper.class})
public class MyBatisConfiguration {
}
