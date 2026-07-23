package com.ymm.coldchainai.rag.knowledge.infrastructure.document;

import com.ymm.coldchainai.rag.knowledge.application.enumtype.RagErrorCodeEnum;
import com.ymm.coldchainai.rag.knowledge.infrastructure.config.InternalRuleRagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 满帮内部规范PDF文档加载器。
 *
 * <p>该类负责完成RAG知识入库前的第一段ETL流程：</p>
 *
 * <p>PDF资源 → PagePdfDocumentReader → Spring AI Document → 业务Metadata补充 → TokenTextSplitter → Chunk Document。</p>
 *
 * <p>当前类只负责把原始PDF加工成知识Chunk，不负责Embedding生成、VectorStore入库和相似度检索。</p>
 *
 * <p>在挖矿流程中，该组件相当于矿场的原料加工车间：PDF是整份地质档案，Document是读取出来的原始矿石，
 * Chunk则是切割成适合后续检测和存储的小块原料。如果不先做好这一步，后面的向量设备只能处理一整块杂乱的大原料。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class InternalRulePdfDocumentLoader {

    /**
     * 内部规范知识类型。
     */
    private static final String INTERNAL_RULE_KNOWLEDGE_TYPE = "internal_rule";

    /**
     * PDF和文本切片配置。
     */
    private final InternalRuleRagProperties internalRuleRagProperties;

    /**
     * Spring资源加载器。
     *
     * <p>用于统一解析classpath:、file:等Spring Resource路径，Loader不需要自己判断当前文件来自classpath还是磁盘。</p>
     */
    private final ResourceLoader resourceLoader;

    /**
     * 读取满帮内部规范PDF并切分成后续可用于Embedding的Document列表。
     *
     * <p>本方法当前只做知识准备，不写VectorStore。下一步PGVector入库服务会直接调用该方法取得最终Chunk。</p>
     *
     * @return 已完成文本读取、Metadata补充和Token切片的Document列表
     */
    public List<Document> loadAndSplitDocumentList() {

        // 在处理PDF之前提前校验RAG配置是否完整，例如PDF路径、文档编码、切片参数等，避免运行到中间阶段才发现配置错误。
        validateConfiguration();

        // 根据配置中的classpath路径解析真实PDF资源。resourceLoader负责屏蔽具体资源来源，当前从classpath读取，未来也可以扩展读取OSS、文件服务器等外部资源。
        Resource pdfResource = resourceLoader.getResource(internalRuleRagProperties.getResourceLocation());

        // 校验PDF资源是否真实存在且当前应用具有读取权限，避免后续PDF Reader因为文件问题产生难以定位的异常。
        if (!pdfResource.exists() || !pdfResource.isReadable()) {
            throw createDocumentLoadException("PDF资源不存在或无法读取，resourceLocation=%s".formatted(internalRuleRagProperties.getResourceLocation()));
        }

        /*
         * PagePdfDocumentReader相当于先把整份PDF按页读取成Spring AI Document。
         * 当前使用它而不是ParagraphPdfDocumentReader，是因为普通Word转PDF不一定包含可靠的PDF目录结构。
         * 这里先定义Reader配置，而不是直接new Reader，是为了把PDF解析行为参数化。
         * 后续如果调整“一页生成一个Document”还是“多页合并一个Document”，只需要修改配置，不需要修改核心流程。
         */
        PdfDocumentReaderConfig pdfDocumentReaderConfig = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(internalRuleRagProperties.getPagesPerDocument())
                .build();

        // 创建PDF读取器，将PDF资源和读取规则绑定。此时只是创建读取工具，还没有真正读取PDF内容。
        PagePdfDocumentReader pagePdfDocumentReader = new PagePdfDocumentReader(pdfResource, pdfDocumentReaderConfig);

        // ListUtils.emptyIfNull统一处理Reader异常返回null的防御场景。Reader正常情况下应该返回List<Document>，这里增加兜底避免第三方组件异常返回null导致空指针。
        List<Document> rawDocumentList = ListUtils.emptyIfNull(pagePdfDocumentReader.read());

        // 如果PDF为空、损坏或者格式无法识别，不能继续进入Metadata补充和Embedding流程。
        if (CollectionUtils.isEmpty(rawDocumentList)) {
            throw createDocumentLoadException("PDF没有解析出任何Document");
        }

        // 在真正切片之前，为原始Document统一补充业务Metadata。Metadata会随着后续Chunk切片一起保留，让每个知识块都知道自己来自哪个文档、哪个版本和什么业务类型。
        List<Document> metadataDocumentList = enrichMetadata(rawDocumentList);

        /*
         * TokenTextSplitter根据Token数量继续把Document切成更小的知识块。
         * 后续用户询问“9点整打卡迟到吗”时，向量检索只需要找最相关的考勤Chunk，不需要把整份公司规范全部交给大模型。
         * 这里创建Splitter对象而不是直接写切片逻辑，是为了复用Spring AI提供的标准切片能力。
         * Token级切分比简单按照字符截断更适合大模型上下文窗口。
         */
        TokenTextSplitter tokenTextSplitter = createTokenTextSplitter();

        // 执行Document切片，把较大的知识文档转换成多个更适合向量检索的小Chunk。ListUtils.emptyIfNull保证第三方Splitter异常返回null时不会污染后续流程。
        List<Document> splitDocumentList = ListUtils.emptyIfNull(tokenTextSplitter.apply(metadataDocumentList));

        // 如果没有生成Chunk，说明后续Embedding没有任何输入，继续执行只会产生空知识库。
        if (CollectionUtils.isEmpty(splitDocumentList)) {
            throw createDocumentLoadException("PDF完成Token切片后没有生成有效Chunk");
        }

        // 最后补充chunkIndex，方便后续VectorStore排查到底命中了哪一块知识。因为一个PDF页面可能被切成多个Chunk，仅靠页码无法精准定位，需要额外记录Chunk顺序。
        List<Document> chunkDocumentList = addChunkIndex(splitDocumentList);

        // documentCode用于定位是哪份知识文档，rawDocumentCount表示PDF解析后的原始页数量，chunkCount表示最终进入Embedding的知识块数量。
        log.info("内部规范PDF读取与切片完成，documentCode={}，rawDocumentCount={}，chunkCount={}",
                internalRuleRagProperties.getDocumentCode(), rawDocumentList.size(), chunkDocumentList.size());

        // 返回不可修改列表，避免调用方拿到结果后意外修改已经加工完成的知识Chunk。后续PGVector入库只应该消费该结果，不应该改变知识生产阶段的数据。
        return List.copyOf(chunkDocumentList);
    }

    /**
     * 为PDF Reader生成的原始Document补充企业业务Metadata。
     *
     * <p>PagePdfDocumentReader负责完成PDF文件解析，它生成的Document通常只包含
     * 文件来源、页码等技术Metadata，例如来自哪个PDF文件、第几页等。
     * 但是企业RAG场景不仅需要知道“这段文字在哪里”，还需要知道“这段知识属于什么业务”。</p>
     *
     * <p>当前方法会在原Metadata基础上增加业务维度信息：
     * 文档编码、文档名称、文档版本和知识类型。
     * 这些Metadata会随着后续TokenTextSplitter切片过程继续传递到每个Chunk，
     * 最终进入Embedding和VectorStore，方便未来检索过滤、版本管理和问题排查。</p>
     *
     * <p>例如：
     * 原始Document只知道：
     * page=3，fileName=internal-rule.pdf。
     * 增强后：
     * page=3，fileName=internal-rule.pdf，
     * documentCode=driver-rule-v1，
     * documentVersion=1.0，
     * knowledgeType=INTERNAL_RULE。</p>
     *
     * <p>在挖矿流程中，该方法相当于给加工前的原矿贴来源标签。
     * 后续即使原矿被切割成多个小钻石，也能通过标签知道每颗钻石来自哪个矿区、哪个批次。</p>
     *
     * @param rawDocumentList PDF Reader生成的原始Document列表
     * @return 已增加业务Metadata的Document列表
     */
    private List<Document> enrichMetadata(List<Document> rawDocumentList) {
        // 创建新的Document集合保存增强后的结果，不直接修改调用方传入的原始列表。后续流程只消费经过业务加工的Document，避免原始解析结果被其他流程意外污染。
        List<Document> metadataDocumentList = new ArrayList<>(rawDocumentList.size());

        // 遍历PDF Reader解析出来的每一个Document，每个Document通常对应PDF中的一页或连续几页内容。
        for (Document rawDocument : rawDocumentList) {

            // 校验Reader返回结果完整性，避免异常Document进入后续切片和Embedding流程。
            if (Objects.isNull(rawDocument)) {
                throw createDocumentLoadException("PDF Reader返回的Document元素不能为空");
            }
            // 空白页没有任何可检索知识，不参与后续Embedding，避免浪费向量存储空间。
            if (StringUtils.isBlank(rawDocument.getText())) {
                // 直接跳过，但不影响其他正常页面继续处理。
                continue;
            }

            /* mutate会基于原Document继续构建新Document，因此PDF Reader已经写入的文件名和页码Metadata不会丢失。
             * mutate会基于当前Document创建新的构建流程。这里不是直接修改原Document，而是在保留原有内容和Metadata的基础上，增加企业业务Metadata。
             * 例如PDF Reader已经记录：page=5
             * fileName=internal-rule.pdf
             * mutate之后仍然保留这些信息，同时新增：
             * documentCode=xxx
             * documentVersion=1.0
             * knowledgeType=INTERNAL_RULE
             */
            Document metadataDocument = rawDocument.mutate()
                    // 保存稳定文档编码，后续可以根据编码区分不同知识文件。
                    .metadata(RagDocumentMetadataKeys.DOCUMENT_CODE, internalRuleRagProperties.getDocumentCode())
                    // 保存文档展示名称，方便日志、检索结果展示和人工排查。
                    .metadata(RagDocumentMetadataKeys.DOCUMENT_NAME, internalRuleRagProperties.getDocumentName())
                    // 保存文档版本，支持未来规则升级后区分旧版本和新版本知识。
                    .metadata(RagDocumentMetadataKeys.DOCUMENT_VERSION, internalRuleRagProperties.getDocumentVersion())
                    // 保存知识类型，例如内部规则、订单知识、支付知识，方便后续检索过滤。
                    .metadata(RagDocumentMetadataKeys.KNOWLEDGE_TYPE, INTERNAL_RULE_KNOWLEDGE_TYPE)
                    // 构建新的Document对象，完成Metadata增强。
                    .build();

            // 将增强后的Document加入结果集合，供后续TokenTextSplitter进行Chunk切分。
            metadataDocumentList.add(metadataDocument);
        }

        // 如果所有页面都是空白，说明该PDF没有任何有效知识，不能继续进入RAG生产流程。
        if (CollectionUtils.isEmpty(metadataDocumentList)) {
            throw createDocumentLoadException("PDF只包含空白页面，没有可用于RAG的有效文本");
        }
        // 返回经过业务Metadata增强后的Document列表。后续切片时，这些Metadata会继续复制到每个Chunk中。
        return metadataDocumentList;
    }

    /**
     * 创建当前内部规范文档使用的Token切片器。
     * <p>TokenTextSplitter负责把经过Metadata增强后的Document继续拆分成更小的知识Chunk，
     * 使后续Embedding和向量检索阶段能够定位到更精确的业务片段。</p>
     *
     * <p>切片策略不能简单按照固定字符长度截断，因为企业规则文档通常存在完整业务语义。
     * 例如“支付成功后锁定10分钟，超时自动释放”属于一个完整规则，如果被强行拆开，可能导致向量检索找到的信息不完整，模型生成错误答案。</p>
     *
     * <p>因此这里同时配置：
     * Chunk目标Token数量、最小字符长度、最大Chunk数量以及中英文标点边界，尽量让切片结果保持业务语义完整。</p>
     *
     * <p>在挖矿流程中，该方法相当于配置矿石切割设备参数：
     * 决定每块钻石切多大、最小保留尺寸以及切割时优先沿什么纹理切割。参数不合理会导致钻石过碎或者携带无效杂质。</p>
     *
     * @return 已根据项目配置初始化的TokenTextSplitter
     */
    private TokenTextSplitter createTokenTextSplitter() {

        /*
         * 配置切片时优先参考的语义边界符号。
         * 对中文企业文档来说，仅使用英文标点无法很好识别句子边界，
         * 因此额外加入中文句号、问号、感叹号和分号。
         * 当Chunk超过目标Token数量时，Splitter会尽量选择这些位置切割，
         * 避免在业务规则中间强行断开。
         */
        List<Character> punctuationMarkList = List.of('。', '！', '？', '；', '\n', '.', '!', '?', ';');

        // 创建TokenTextSplitter，并使用配置文件中的参数控制切片行为。
        return TokenTextSplitter.builder()
                // 控制每个Chunk目标Token数量，影响单个知识块大小。太大会导致检索粒度过粗，太小会导致上下文信息不足。
                .withChunkSize(internalRuleRagProperties.getChunkSize())

                // 控制Chunk允许保留的最小字符数量。防止切割过程中产生大量过短、没有业务意义的小文本块。
                .withMinChunkSizeChars(internalRuleRagProperties.getMinChunkSizeChars())

                // 控制进入Embedding流程的最小文本长度。过短文本通常无法表达完整语义，没有必要浪费向量存储空间。
                .withMinChunkLengthToEmbed(internalRuleRagProperties.getMinChunkLengthToEmbed())

                // 限制单个文档最多生成多少Chunk。防止异常大文件或者解析异常导致生成海量向量，影响Embedding和VectorStore压力。
                .withMaxNumChunks(internalRuleRagProperties.getMaxNumChunks())

                // 控制切片后是否保留原始分隔符。保留分隔符可以让Chunk上下文更加自然，例如保留句号帮助模型理解句子结束。
                .withKeepSeparator(Boolean.TRUE.equals(internalRuleRagProperties.getKeepSeparator()))

                // 设置中文和英文业务文档常见标点作为优先切割位置。
                .withPunctuationMarks(punctuationMarkList)

                // 完成Builder构建，生成不可修改的TokenTextSplitter实例。
                .build();
    }

    /**
     * 为Token切片后的最终知识Chunk增加稳定顺序Metadata。
     *
     * <p>TokenTextSplitter会把一个较大的Document拆分成多个更小的知识块。
     * 这些Chunk进入Embedding和VectorStore后，后续线上排查“模型为什么引用了这段知识”
     * 时，需要知道具体命中了哪一个Chunk，因此这里增加chunkIndex作为人工可读的定位标识。</p>
     *
     * <p>当前方法不会直接修改原Document，而是通过mutate()基于原Chunk创建新的Document，
     * 保留原有Metadata（例如documentCode、documentVersion、page等），
     * 只额外增加chunkIndex字段。</p>
     *
     * <p>chunkIndex从1开始，而不是从Java数组下标0开始，
     * 因为该字段未来会进入日志、数据库和人工排查场景。
     * 人员看到chunkIndex=1比chunkIndex=0更符合实际业务阅读习惯。</p>
     *
     * RAG知识入库要求：要么整批成功，要么整批失败。
     * 所以for循环内抛出异常整个进程会结束，知识加工流水线必须保证输入完整、输出可追踪、异常立即终止，不能产生半成功数据。
     *
     * @param splitDocumentList TokenTextSplitter生成的最终知识Chunk列表
     * @return 已增加chunkIndex Metadata的不可修改Chunk列表
     */
    private List<Document> addChunkIndex(List<Document> splitDocumentList) {
        List<Document> chunkDocumentList = new ArrayList<>(splitDocumentList.size());

        for (int index = 0; index < splitDocumentList.size(); index++) {
            Document splitDocument = splitDocumentList.get(index);

            if (Objects.isNull(splitDocument) || StringUtils.isBlank(splitDocument.getText())) {
                throw createDocumentLoadException("Token切片结果包含无效Document，index=%s".formatted(index));
            }

            // chunkIndex从1开始，方便后续日志、数据库和人工排查时直接阅读。
            Document chunkDocument = splitDocument.mutate()
                    .metadata(RagDocumentMetadataKeys.CHUNK_INDEX, index + 1)
                    .build();

            chunkDocumentList.add(chunkDocument);
        }

        return chunkDocumentList;
    }

    /**
     * 校验内部规范PDF读取和切片配置。
     */
    private void validateConfiguration() {
        if (StringUtils.isBlank(internalRuleRagProperties.getResourceLocation())) {
            throw createConfigurationException("resourceLocation不能为空");
        }

        if (StringUtils.isBlank(internalRuleRagProperties.getDocumentCode())) {
            throw createConfigurationException("documentCode不能为空");
        }

        if (StringUtils.isBlank(internalRuleRagProperties.getDocumentName())) {
            throw createConfigurationException("documentName不能为空");
        }

        if (StringUtils.isBlank(internalRuleRagProperties.getDocumentVersion())) {
            throw createConfigurationException("documentVersion不能为空");
        }

        if (Objects.isNull(internalRuleRagProperties.getPagesPerDocument()) || internalRuleRagProperties.getPagesPerDocument() <= 0) {
            throw createConfigurationException("pagesPerDocument必须大于0");
        }

        if (Objects.isNull(internalRuleRagProperties.getChunkSize()) || internalRuleRagProperties.getChunkSize() <= 0) {
            throw createConfigurationException("chunkSize必须大于0");
        }

        if (Objects.isNull(internalRuleRagProperties.getMinChunkSizeChars()) || internalRuleRagProperties.getMinChunkSizeChars() <= 0) {
            throw createConfigurationException("minChunkSizeChars必须大于0");
        }

        if (Objects.isNull(internalRuleRagProperties.getMinChunkLengthToEmbed()) || internalRuleRagProperties.getMinChunkLengthToEmbed() <= 0) {
            throw createConfigurationException("minChunkLengthToEmbed必须大于0");
        }

        if (Objects.isNull(internalRuleRagProperties.getMaxNumChunks()) || internalRuleRagProperties.getMaxNumChunks() <= 0) {
            throw createConfigurationException("maxNumChunks必须大于0");
        }
    }

    /**
     * 创建RAG文档配置异常。
     *
     * @param detailMessage 具体配置错误
     * @return 配置异常
     */
    private IllegalStateException createConfigurationException(String detailMessage) {
        String errorMessage = "%s：%s".formatted(RagErrorCodeEnum.RAG_DOCUMENT_CONFIGURATION_ERROR.getMessage(), detailMessage);
        return new IllegalStateException(errorMessage);
    }

    /**
     * 创建RAG文档读取异常。
     *
     * @param detailMessage 具体读取错误
     * @return 文档读取异常
     */
    private IllegalStateException createDocumentLoadException(String detailMessage) {
        String errorMessage = "%s：%s".formatted(RagErrorCodeEnum.RAG_DOCUMENT_LOAD_ERROR.getMessage(), detailMessage);
        return new IllegalStateException(errorMessage);
    }
}
