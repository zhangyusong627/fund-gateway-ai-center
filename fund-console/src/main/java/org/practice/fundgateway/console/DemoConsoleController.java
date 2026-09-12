package org.practice.fundgateway.console;

import org.practice.fundgateway.console.ConsoleModels.ConsoleStatus;
import org.practice.fundgateway.console.ConsoleModels.ConsoleOverview;
import org.practice.fundgateway.console.ConsoleModels.CreateEvaluationSetRequest;
import org.practice.fundgateway.console.ConsoleModels.EvaluationRunSummary;
import org.practice.fundgateway.console.ConsoleModels.EvaluationSetSummary;
import org.practice.fundgateway.console.ConsoleModels.GuardianSimulationRequest;
import org.practice.fundgateway.console.ConsoleModels.GuardianSimulationResponse;
import org.practice.fundgateway.console.ConsoleModels.RagQueryRequest;
import org.practice.fundgateway.console.ConsoleModels.RagQueryResponse;
import org.practice.fundgateway.console.ConsoleModels.RagQueryAudit;
import org.practice.fundgateway.console.ConsoleModels.PublishedCollection;
import org.practice.fundgateway.console.ConsoleModels.RagEvaluationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供可视化演示控制台需要的状态、RAG 和智能守护接口。 */
@RestController
@RequestMapping("/api/console")
public class DemoConsoleController {

    private final ConsoleRagService ragService;
    private final GuardianConsoleService guardianService;
    private final ConsoleOverviewService overviewService;
    private final RagEvaluationService evaluationService;
    private final String persistenceMode;

    /** 注入两个可独立演示的业务能力。 */
    public DemoConsoleController(ConsoleRagService ragService, GuardianConsoleService guardianService,
                                 ConsoleOverviewService overviewService, RagEvaluationService evaluationService,
                                 @Value("${console.persistence.mode:memory}") String persistenceMode) {
        this.ragService = ragService;
        this.guardianService = guardianService;
        this.overviewService = overviewService;
        this.evaluationService = evaluationService;
        this.persistenceMode = persistenceMode;
    }

    /** 返回首页需要的真实业务计数和逐项依赖状态。 */
    @GetMapping("/overview")
    public ConsoleOverview overview() {
        return overviewService.load();
    }

    /** 返回依赖就绪状态和实际技术基线。 */
    @GetMapping("/status")
    public ConsoleStatus status() {
        return new ConsoleStatus(ragService.ready() ? "READY" : "DEGRADED",
                "PostgreSQL 16 + pgvector 混合检索", "BAAI/bge-small-zh-v1.5",
                512, "十秒窗口 + 六十秒冷却 + Java 门禁", guardianService.deepSeekAvailable(),
                persistenceMode.toUpperCase(java.util.Locale.ROOT), ragService.publishedCollectionCount());
    }

    /** 返回在线检索可以选择的已发布知识集合。 */
    @GetMapping("/rag/collections")
    public List<PublishedCollection> publishedCollections() {
        return ragService.publishedCollections();
    }

    /** 查询全部与文档版本绑定的评测集。 */
    @GetMapping("/rag/evaluation-sets")
    public List<EvaluationSetSummary> evaluationSets() {
        return evaluationService.findSets();
    }

    /** 创建一个不可变评测集版本。 */
    @PostMapping("/rag/evaluation-sets")
    public EvaluationSetSummary createEvaluationSet(@RequestBody CreateEvaluationSetRequest request) {
        return evaluationService.createSet(request);
    }

    /** 运行指定评测集并持久化逐题结果。 */
    @PostMapping("/rag/evaluation-sets/{setId}/runs")
    public RagEvaluationResponse evaluateRag(@org.springframework.web.bind.annotation.PathVariable UUID setId)
            throws Exception {
        return evaluationService.run(setId);
    }

    /** 查询历史评测批次，可按评测集过滤。 */
    @GetMapping("/rag/evaluation-runs")
    public List<EvaluationRunSummary> evaluationRuns(
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID setId) {
        return evaluationService.findRuns(setId);
    }

    /** 触发一次真实 RAG 检索。 */
    @PostMapping("/rag/query")
    public RagQueryResponse ragQuery(@RequestBody RagQueryRequest request) throws Exception {
        return ragService.query(request);
    }

    /** 返回最近在线检索审计，供检索实验室复盘。 */
    @GetMapping("/rag/query-audits")
    public List<RagQueryAudit> queryAudits() {
        return ragService.queryAudits();
    }

    /** 触发一次智能守护指标回放。 */
    @PostMapping("/guardian/simulate")
    public GuardianSimulationResponse guardianSimulation(@RequestBody GuardianSimulationRequest request)
            throws Exception {
        return guardianService.simulate(request);
    }

    /** 把输入或运行错误转换为页面可展示的错误对象。 */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ConsoleError> badRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(new ConsoleError("CONSOLE_REQUEST_FAILED", exception.getMessage()));
    }

    /** 把依赖调用错误转换为明确的服务不可用响应。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ConsoleError> dependencyFailure(Exception exception) {
        return ResponseEntity.internalServerError()
                .body(new ConsoleError("CONSOLE_DEPENDENCY_FAILED", exception.getMessage()));
    }

    /** 页面统一错误结构。 */
    public record ConsoleError(String code, String message) {
    }
}
