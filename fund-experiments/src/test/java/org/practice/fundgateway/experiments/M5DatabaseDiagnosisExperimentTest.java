package org.practice.fundgateway.experiments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.practice.fundgateway.guardian.metrics.GuardianRiskRepository;

/** 验证 M5 模型调用异常会结束任务并留下安全的失败证据。 */
class M5DatabaseDiagnosisExperimentTest {

    @TempDir
    private Path evidenceDir;

    /** 模型调用抛异常时必须回写 FAILED、原样抛出并且不落错误详情。 */
    @Test
    void modelFailureMarksTaskFailedAndWritesSafeEvidence() throws Exception {
        RecordingRiskRepository repository = new RecordingRiskRepository();
        UUID taskId = UUID.fromString("2c3f9a9a-5b5f-4e4f-9e68-14e5c2f80f91");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> M5DatabaseDiagnosisExperiment.invokeModel(repository, taskId, evidenceDir,
                        () -> { throw new IllegalStateException("provider response contained secret-shaped data"); }));

        assertEquals("provider response contained secret-shaped data", failure.getMessage());
        assertEquals(taskId, repository.failedTaskId);
        assertEquals("FAILED", repository.failedStatus);
        Path evidence = evidenceDir.resolve("model-failure.txt");
        assertTrue(Files.isRegularFile(evidence));
        assertEquals("IllegalStateException", Files.readString(evidence));
    }

    /** 记录失败回写的最小仓储替身，不连接数据库也不依赖 Mockito agent。 */
    private static final class RecordingRiskRepository extends GuardianRiskRepository {

        private UUID failedTaskId;
        private String failedStatus;

        /** 父类只保存 JDBC 引用，本测试不触发任何真实 JDBC 调用。 */
        private RecordingRiskRepository() {
            super(null);
        }

        /** 记录模型失败时的任务状态更新。 */
        @Override
        public boolean updateDiagnosticTaskStatus(UUID taskId, String status) {
            failedTaskId = taskId;
            failedStatus = status;
            return true;
        }
    }
}
