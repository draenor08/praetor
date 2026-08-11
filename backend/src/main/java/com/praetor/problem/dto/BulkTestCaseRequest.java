package com.praetor.problem.dto;

import java.util.List;

public record BulkTestCaseRequest(
        String mode,
        List<TestCaseItem> cases) {
}
