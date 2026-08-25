package com.praetor.submission.dto;

import java.util.List;

public record SubmissionPage(
        List<SubmissionSummary> content,
        Integer page,
        Integer size,
        Long totalElements) {
}
