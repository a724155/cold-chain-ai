package com.ymm.coldchainai.verification.interfaces.web;

import com.ymm.coldchainai.shared.response.YmmResult;
import com.ymm.coldchainai.verification.application.service.IDatabaseVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据库连通性验证接口。
 *
 * <p>该接口用于第一阶段验证 HTTP、Application Service、MyBatis Mapper、
 * Mapper XML、DataSource 和 MySQL 之间的完整调用链。</p>
 *
 * <p>Controller 只负责接收请求和封装统一返回结果，
 * 不直接调用 Mapper，也不直接执行 SQL。</p>
 */
@RestController
@RequestMapping("/api/verification/database")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DatabaseVerificationController {

    /**
     * 数据库连通性验证服务。
     */
    private final IDatabaseVerificationService databaseVerificationService;

    /**
     * 验证 MyBatis 和 MySQL 是否真正连通。
     *
     * @return 包含数据库验证数值的统一成功结果
     */
    @GetMapping("/connection")
    public YmmResult<Integer> verifyDatabaseConnection() {
        // 调用 Application Service 执行数据库验证，Controller 不直接依赖 Mapper。
        Integer databaseConnectionValue = databaseVerificationService.verifyDatabaseConnection();

        // 将数据库返回的验证数值封装成统一成功结果。
        return YmmResult.success(databaseConnectionValue);
    }
}
