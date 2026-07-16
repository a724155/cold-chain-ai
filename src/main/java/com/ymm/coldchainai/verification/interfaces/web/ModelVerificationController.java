package com.ymm.coldchainai.verification.interfaces.web;

import com.ymm.coldchainai.verification.application.service.IModelVerificationService;
import com.ymm.coldchainai.verification.interfaces.web.request.ModelChatRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通模型调用验证接口。
 *
 * <p>该接口用于第一阶段验证 HTTP、Spring MVC、Spring AI 和模型服务之间的完整调用链。</p>
 *
 * <p>Controller 只负责接收和校验 HTTP 请求，然后调用 Application Service，
 * 不直接操作 ChatClient，也不承担具体模型调用逻辑。</p>
 */
@RestController
@RequestMapping("/api/verification/model")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ModelVerificationController {

    /**
     * 普通模型调用验证服务。
     */
    private final IModelVerificationService modelVerificationService;

    /**
     * 调用基础模型并返回完整回答。
     *
     * @param request 模型调用验证请求
     * @return 模型完整回答
     */
    @PostMapping("/chat")
    public String chat(@Valid @RequestBody ModelChatRequest request) {
        // Controller 只提取 HTTP 请求参数，具体模型调用由 Application Service 负责。
        return modelVerificationService.chat(request.getQuestion());
    }
}
