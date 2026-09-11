package org.practice.fundgateway.experiments;

import java.nio.file.Path;

import org.postgresql.ds.PGSimpleDataSource;
import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;
import org.practice.fundgateway.knowledge.document.DocumentElement;
import org.practice.fundgateway.knowledge.document.DocxDocumentParser;
import org.practice.fundgateway.knowledge.document.ParsedDocument;
import org.practice.fundgateway.knowledge.embedding.EmbeddingDescriptor;
import org.practice.fundgateway.knowledge.embedding.LocalBgeEmbeddingModel;
import org.practice.fundgateway.knowledge.persistence.PgvectorKnowledgeVectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** M3 通用协议证据索引：把原始 DOCX 中的 HTTP 方法声明补入 M2 集合。 */
@Component
@Profile("m3-index")
public class M3ProtocolEvidenceIndexExperiment implements CommandLineRunner {

    private static final String COLLECTION = "m2_shengheng_credit_application_v1";
    private static final Path MODEL_PATH = resolvePath("models", "bge-small-zh-v1.5");
    private static final Path DOCUMENT_PATH = Path.of(
            "/Users/zhangyusong/Downloads/求职面试/金融机构标准接口文档/升恒消费金融接口文档.docx");
    private static final EmbeddingDescriptor DESCRIPTOR = new EmbeddingDescriptor(
            "local", "BAAI/bge-small-zh-v1.5", 512, true);

    /** 启动一次通用协议证据解析、向量化和幂等写入。 */
    @Override
    public void run(String... args) throws Exception {
        ParsedDocument document = new DocxDocumentParser().parse(
                DOCUMENT_PATH, "shengheng-consumer", "source-v1");
        DocumentElement methodElement = document.elements().stream()
                .filter(element -> element.cleanedText().contains("POST请求"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到 HTTP 方法原文"));
        KnowledgeChunk chunk = new KnowledgeChunk(
                "shengheng-consumer:source-v1:protocol-post",
                document.source(),
                "通用报文传递",
                methodElement.cleanedText(),
                methodElement.sequence(),
                methodElement.sequence(),
                -1,
                -1);
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:55432/fund_integration");
        dataSource.setUser("fund_demo");
        dataSource.setPassword("fund_demo_password");
        PgvectorKnowledgeVectorStore store = new PgvectorKnowledgeVectorStore(new JdbcTemplate(dataSource));
        store.ensureCollection(COLLECTION, "M2 授信申请及通用协议证据", DESCRIPTOR);
        try (LocalBgeEmbeddingModel embedding = new LocalBgeEmbeddingModel(MODEL_PATH)) {
            store.save(COLLECTION, chunk, embedding.embed(chunk.text()), DESCRIPTOR);
        }
        System.out.println("M3 通用协议证据索引完成，chunkId=" + chunk.chunkId()
                + "，locator=" + chunk.sectionPath() + "#" + chunk.firstSequence());
    }

    /** 兼容从仓库根目录或模块目录启动。 */
    private static Path resolvePath(String first, String second) {
        Path rootRelative = Path.of(first, second);
        return java.nio.file.Files.exists(rootRelative) ? rootRelative : Path.of("..", first, second);
    }
}
