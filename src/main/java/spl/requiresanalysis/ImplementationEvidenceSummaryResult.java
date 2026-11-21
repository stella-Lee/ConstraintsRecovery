package spl.requiresanalysis;

import java.util.List;
import java.util.Objects;

public record ImplementationEvidenceSummaryResult(
        int inputBundleCount,
        int summaryCount,
        List<ImplementationEvidenceSummary> summaries
) {
    public ImplementationEvidenceSummaryResult {
        summaries = List.copyOf(Objects.requireNonNull(summaries, "summaries"));
    }
}
