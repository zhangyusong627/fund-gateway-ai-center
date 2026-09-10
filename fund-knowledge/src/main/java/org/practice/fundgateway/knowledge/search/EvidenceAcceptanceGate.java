package org.practice.fundgateway.knowledge.search;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** 判断检索候选是否具备支撑回答所需的关键词和来源定位。 */
public class EvidenceAcceptanceGate {

    /** 对候选证据执行确定性接受检查。 */
    public AcceptanceResult evaluate(List<HybridPgvectorRetriever.HybridRetrievedChunk> candidates,
                                     Set<String> requiredKeywords) {
        if (requiredKeywords == null || requiredKeywords.isEmpty()) {
            throw new IllegalArgumentException("必需关键词不能为空");
        }
        String joinedContent = candidates.stream()
                .map(candidate -> candidate.chunk().content().toLowerCase())
                .collect(Collectors.joining(" "));
        Set<String> missing = requiredKeywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .filter(keyword -> !joinedContent.contains(keyword.toLowerCase()))
                .collect(Collectors.toCollection(TreeSet::new));
        boolean hasCitation = candidates.stream().anyMatch(candidate -> {
            PgvectorRetriever.RetrievedChunk chunk = candidate.chunk();
            return chunk.documentId() != null && chunk.documentVersion() != null
                    && chunk.sectionPath() != null && chunk.locator() != null;
        });
        boolean accepted = !candidates.isEmpty() && missing.isEmpty() && hasCitation;
        return new AcceptanceResult(accepted, missing, hasCitation);
    }

    /** 表示证据是否被接受及未满足的确定性条件。 */
    public record AcceptanceResult(boolean accepted, Set<String> missingKeywords,
                                   boolean hasCitation) {
    }
}
