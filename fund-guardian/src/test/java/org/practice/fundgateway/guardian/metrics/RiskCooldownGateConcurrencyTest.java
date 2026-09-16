package org.practice.fundgateway.guardian.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/** 验证同一风险在并发请求下只能获得一次冷却资格。 */
class RiskCooldownGateConcurrencyTest {

    /** 并发竞争同一风险时只允许一个请求通过。 */
    @Test
    void shouldAcquireOnlyOnceConcurrently() throws Exception {
        RiskCooldownGate gate = new RiskCooldownGate(Duration.ofMinutes(1));
        ExecutorService executor = Executors.newFixedThreadPool(16);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            results.add(executor.submit(() -> gate.tryAcquire("same-risk",
                    Instant.parse("2026-09-20T00:00:00Z"))));
        }
        executor.shutdown();

        long acquired = 0;
        for (Future<Boolean> result : results) {
            if (result.get(5, TimeUnit.SECONDS)) {
                acquired++;
            }
        }
        assertEquals(1, acquired);
    }
}
