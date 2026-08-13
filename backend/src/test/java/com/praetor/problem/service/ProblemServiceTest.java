package com.praetor.problem.service;

import com.praetor.identity.entity.User;
import com.praetor.problem.dto.ProblemRequest;
import com.praetor.problem.entity.Problem;
import com.praetor.problem.repository.ProblemRepository;
import com.praetor.problem.repository.ProblemTagRepository;
import com.praetor.problem.repository.ProblemUsageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemServiceTest {

    private final ProblemRepository problemRepository =
            mock(ProblemRepository.class);

    private final ProblemUsageRepository usageRepository =
            mock(ProblemUsageRepository.class);

    private final ProblemTagRepository tagRepository =
            mock(ProblemTagRepository.class);

    private final ProblemService service =
            new ProblemService(
                    problemRepository,
                    usageRepository,
                    tagRepository);

    @Test
    void problemSetterCanCreateProblem() {

        User setter = user(5L, "PROBLEM_SETTER");

        when(problemRepository.existsBySlug("a-plus-b"))
                .thenReturn(false);

        when(problemRepository.save(any(Problem.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        var response =
                service.create(
                        validRequest("a-plus-b"),
                        setter);

        assertThat(response.slug())
                .isEqualTo("a-plus-b");

        assertThat(response.title())
                .isEqualTo("A Plus B");

        assertThat(response.difficulty())
                .isEqualTo(800);

        assertThat(response.createdBy())
                .isEqualTo(5L);

        verify(problemRepository)
                .save(any(Problem.class));
    }

    @Test
    void normalUserCannotCreateProblem() {

        User normalUser =
                user(5L, "USER");

        Throwable t = catchThrowable(() ->
                service.create(
                        validRequest("a-plus-b"),
                        normalUser));

        assertStatus(
                t,
                HttpStatus.FORBIDDEN);

        verify(problemRepository, never())
                .save(any());
    }

    /**
     * Tags are a shared vocabulary, so they are normalised on write rather than stored as typed —
     * otherwise "Math", "math " and "math" become three tags that each filter differently.
     */
    @Test
    void tagsAreLowercasedTrimmedAndDeduplicatedOnCreate() {

        User setter = user(5L, "PROBLEM_SETTER");

        when(problemRepository.existsBySlug("tagged"))
                .thenReturn(false);

        when(problemRepository.save(any(Problem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(
                taggedRequest("tagged", List.of("  Math ", "GREEDY", "math", "")),
                setter);

        // Replace-not-merge: the old rows go before the new ones land, or a tag could never be
        // removed. (The problem id is IDENTITY-generated, so it is only real against a database —
        // the id reaching problem_tags is covered by the live end-to-end run, not here.)
        verify(tagRepository).deleteTagsOfProblem(any());
        verify(tagRepository).insertTagIfAbsent("math");
        verify(tagRepository).insertTagIfAbsent("greedy");
        verify(tagRepository, never()).insertTagIfAbsent("Math");
        verify(tagRepository, never()).insertTagIfAbsent("");
    }

    /** Null tags means "this request is not about tags" — a client that omits them must not wipe them. */
    @Test
    void omittingTagsLeavesThemUntouched() {

        User setter = user(5L, "PROBLEM_SETTER");

        when(problemRepository.existsBySlug("a-plus-b"))
                .thenReturn(false);

        when(problemRepository.save(any(Problem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(
                validRequest("a-plus-b"),
                setter);

        verify(tagRepository, never()).deleteTagsOfProblem(any());
    }

    @Test
    void tooManyTagsGets400() {

        User setter = user(5L, "PROBLEM_SETTER");

        Throwable t = catchThrowable(() ->
                service.create(
                        taggedRequest("tagged",
                                List.of("a", "b", "c", "d", "e", "f", "g", "h", "i")),
                        setter));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);
    }

    /**
     * The list filter passes selected tags to SQL as one comma-separated string, so a comma inside a
     * stored name would split into two filters and quietly widen every search using it.
     */
    @Test
    void tagWithCommaGets400() {

        User setter = user(5L, "PROBLEM_SETTER");

        Throwable t = catchThrowable(() ->
                service.create(
                        taggedRequest("tagged", List.of("math,greedy")),
                        setter));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);
    }

    /**
     * The engine cannot run a custom checker, so a SPECIAL problem would be authorable and
     * submittable but never judgeable. Both write paths must refuse it, not just create.
     */
    @Test
    void specialJudgeModeIsRejectedOnCreate() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        Throwable t = catchThrowable(() ->
                service.create(
                        specialRequest("custom-checked"),
                        setter));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);

        assertThat(t)
                .hasMessageContaining("SPECIAL");

        verify(problemRepository, never())
                .save(any());
    }

    @Test
    void specialJudgeModeIsRejectedOnUpdate() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        Throwable t = catchThrowable(() ->
                service.update(
                        "a-plus-b",
                        specialRequest("a-plus-b"),
                        setter));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);

        verify(problemRepository, never())
                .save(any());
    }

    @Test
    void duplicateSlugGets409() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        when(problemRepository.existsBySlug("a-plus-b"))
                .thenReturn(true);

        Throwable t = catchThrowable(() ->
                service.create(
                        validRequest("a-plus-b"),
                        setter));

        assertStatus(
                t,
                HttpStatus.CONFLICT);

        verify(problemRepository, never())
                .save(any());
    }

    @Test
    void problemSetterCanUpdateProblem() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        Problem existing =
                new Problem(
                        "old-problem",
                        "Old Title",
                        "Old statement",
                        null,
                        800,
                        1000,
                        262144,
                        "EXACT",
                        null,
                        null,
                        null,
                        5L);

        when(problemRepository
                .findBySlug("old-problem"))
                .thenReturn(
                        Optional.of(existing));

        when(problemRepository
                .findBySlug("new-problem"))
                .thenReturn(
                        Optional.empty());

        when(problemRepository.save(existing))
                .thenReturn(existing);

        ProblemRequest request =
                new ProblemRequest(
                        "new-problem",
                        "New Title",
                        "New statement",
                        "n <= 100",
                        1200,
                        2000,
                        131072,
                        "TOKEN",
                        null,
                        null,
                        "Editorial", null, null);

        var response =
                service.update(
                        "old-problem",
                        request,
                        setter);

        assertThat(response.slug())
                .isEqualTo("new-problem");

        assertThat(response.title())
                .isEqualTo("New Title");

        assertThat(response.difficulty())
                .isEqualTo(1200);

        assertThat(response.judgeMode())
                .isEqualTo("TOKEN");

        verify(problemRepository)
                .save(existing);
    }

    @Test
    void adminCanDeleteProblem() {

        User admin =
                user(1L, "ADMIN");

        Problem problem =
                new Problem(
                        "remove-me",
                        "Remove Me",
                        "statement",
                        null,
                        800,
                        1000,
                        262144,
                        "EXACT",
                        null,
                        null,
                        null,
                        5L);

        when(problemRepository
                .findBySlug("remove-me"))
                .thenReturn(
                        Optional.of(problem));

        service.delete(
                "remove-me",
                admin);

        verify(problemRepository)
                .delete(problem);
    }

    @Test
    void adminCanCreateProblem() {

        User admin = user(1L, "ADMIN");

        when(problemRepository.existsBySlug("a-plus-b"))
                .thenReturn(false);

        when(problemRepository.save(any(Problem.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        var response =
                service.create(
                        validRequest("a-plus-b"),
                        admin);

        assertThat(response.slug())
                .isEqualTo("a-plus-b");
    }

    @Test
    void problemSetterCanDeleteUnusedProblem() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        Problem problem = problem("remove-me");

        when(problemRepository
                .findBySlug("remove-me"))
                .thenReturn(
                        Optional.of(problem));

        service.delete(
                "remove-me",
                setter);

        verify(problemRepository)
                .delete(problem);
    }

    @Test
    void normalUserCannotDeleteProblem() {

        Throwable t = catchThrowable(() ->
                service.delete(
                        "a-plus-b",
                        user(5L, "USER")));

        assertStatus(
                t,
                HttpStatus.FORBIDDEN);

        verify(problemRepository, never())
                .delete(any());
    }

    @Test
    void deleteBlockedWhileAContestUsesTheProblem() {

        when(problemRepository
                .findBySlug("in-contest"))
                .thenReturn(
                        Optional.of(problem("in-contest")));

        when(usageRepository.findUsingContestTitle(any()))
                .thenReturn(
                        Optional.of("Praetor Demo Round 1"));

        Throwable t = catchThrowable(() ->
                service.delete(
                        "in-contest",
                        user(1L, "ADMIN")));

        assertStatus(t, HttpStatus.CONFLICT);

        assertThat(((ResponseStatusException) t).getReason())
                .contains("Praetor Demo Round 1")
                .contains("archive");

        verify(problemRepository, never())
                .delete(any());
    }

    @Test
    void deleteBlockedWhileSubmissionsReferenceTheProblem() {

        when(problemRepository
                .findBySlug("submitted-to"))
                .thenReturn(
                        Optional.of(problem("submitted-to")));

        when(usageRepository.countSubmissions(any()))
                .thenReturn(47L);

        Throwable t = catchThrowable(() ->
                service.delete(
                        "submitted-to",
                        user(1L, "ADMIN")));

        assertStatus(t, HttpStatus.CONFLICT);

        assertThat(((ResponseStatusException) t).getReason())
                .contains("47");

        verify(problemRepository, never())
                .delete(any());
    }

    @Test
    void liveContestFreezesJudgingFields() {

        Problem existing = problem("a-plus-b");

        when(problemRepository.findBySlug("a-plus-b"))
                .thenReturn(Optional.of(existing));

        when(usageRepository.existsLiveContestForProblem(any()))
                .thenReturn(true);

        // same slug, same everything — except the time limit
        ProblemRequest request =
                new ProblemRequest(
                        "a-plus-b",
                        "A Plus B",
                        "Add two numbers.",
                        "1 <= a,b <= 100",
                        800,
                        5000,
                        262144,
                        "EXACT",
                        null,
                        null,
                        null, null, null);

        Throwable t = catchThrowable(() ->
                service.update(
                        "a-plus-b",
                        request,
                        user(5L, "PROBLEM_SETTER")));

        assertStatus(t, HttpStatus.CONFLICT);

        verify(problemRepository, never())
                .save(any());
    }

    @Test
    void liveContestStillAllowsStatementEdits() {

        Problem existing = problem("a-plus-b");

        when(problemRepository.findBySlug("a-plus-b"))
                .thenReturn(Optional.of(existing));

        when(usageRepository.existsLiveContestForProblem(any()))
                .thenReturn(true);

        when(problemRepository.save(existing))
                .thenReturn(existing);

        // judging fields untouched; only the prose changes
        ProblemRequest request =
                new ProblemRequest(
                        "a-plus-b",
                        "A Plus B",
                        "Add two numbers. Note: they fit in a 64-bit integer.",
                        "1 <= a,b <= 100",
                        800,
                        1000,
                        262144,
                        "EXACT",
                        null,
                        null,
                        null, null, null);

        var response =
                service.update(
                        "a-plus-b",
                        request,
                        user(5L, "PROBLEM_SETTER"));

        assertThat(response.statement())
                .contains("64-bit");
    }

    @Test
    void archiveHidesProblemWithoutDeletingIt() {

        Problem problem = problem("retired");

        when(problemRepository.findBySlug("retired"))
                .thenReturn(Optional.of(problem));

        when(problemRepository.save(problem))
                .thenReturn(problem);

        var response =
                service.setArchived(
                        "retired",
                        true,
                        user(5L, "PROBLEM_SETTER"));

        assertThat(response.archived()).isTrue();

        verify(problemRepository, never())
                .delete(any());
    }

    @Test
    void usageReportsWhyDeleteIsBlocked() {

        when(problemRepository.findBySlug("a-plus-b"))
                .thenReturn(
                        Optional.of(problem("a-plus-b")));

        when(usageRepository.countSubmissions(any()))
                .thenReturn(3L);

        when(usageRepository.existsLiveContestForProblem(any()))
                .thenReturn(true);

        var usage =
                service.usage(
                        "a-plus-b",
                        user(1L, "ADMIN"));

        assertThat(usage.deletable()).isFalse();
        assertThat(usage.submissions()).isEqualTo(3L);
        assertThat(usage.inLiveContest()).isTrue();
        assertThat(usage.reason()).contains("3");
    }

    @Test
    void managementListMarksUsedProblemsUndeletable() {

        // built first: row() stubs its own mock, which Mockito rejects inside an open when(...)
        var free = managedRow("free", 0, 0, 0);
        var inContest = managedRow("in-contest", 0, 1, 0);
        var submitted = managedRow("submitted", 5, 0, 0);

        when(usageRepository.findManagementRows())
                .thenReturn(List.of(free, inContest, submitted));

        var rows = service.listForManagement(user(1L, "ADMIN"));

        assertThat(rows).hasSize(3);

        assertThat(rows.get(0).deletable()).isTrue();
        assertThat(rows.get(0).lockReason()).isNull();

        assertThat(rows.get(1).deletable()).isFalse();
        assertThat(rows.get(1).lockReason()).contains("contest");

        assertThat(rows.get(2).deletable()).isFalse();
        assertThat(rows.get(2).lockReason()).contains("5");
    }

    @Test
    void normalUserCannotListForManagement() {

        Throwable t = catchThrowable(() ->
                service.listForManagement(user(5L, "USER")));

        assertStatus(t, HttpStatus.FORBIDDEN);

        verify(usageRepository, never()).findManagementRows();
    }

    @Test
    void managementFetchOfUnknownProblemGets404() {

        when(problemRepository.findBySlug("ghost"))
                .thenReturn(Optional.empty());

        Throwable t = catchThrowable(() ->
                service.getForManagement("ghost", user(5L, "PROBLEM_SETTER")));

        assertStatus(t, HttpStatus.NOT_FOUND);
    }

    @Test
    void normalUserCannotReadUsage() {

        Throwable t = catchThrowable(() ->
                service.usage(
                        "a-plus-b",
                        user(5L, "USER")));

        assertStatus(t, HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidDifficultyGets400() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        ProblemRequest request =
                new ProblemRequest(
                        "bad-problem",
                        "Bad Problem",
                        "statement",
                        null,
                        5000,
                        1000,
                        262144,
                        "EXACT",
                        null,
                        null,
                        null, null, null);

        Throwable t = catchThrowable(() ->
                service.create(
                        request,
                        setter));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);

        verify(problemRepository, never())
                .save(any());
    }

    @Test
    void floatModeRequiresPositiveEpsilon() {

        User setter =
                user(5L, "PROBLEM_SETTER");

        ProblemRequest request =
                new ProblemRequest(
                        "float-problem",
                        "Float Problem",
                        "statement",
                        null,
                        800,
                        1000,
                        262144,
                        "FLOAT",
                        null,
                        null,
                        null, null, null);

        Throwable t = catchThrowable(() ->
                service.create(
                        request,
                        setter));

        assertStatus(
                t,
                HttpStatus.BAD_REQUEST);

        verify(problemRepository, never())
                .save(any());
    }

    private ProblemRequest validRequest(
            String slug) {

        return new ProblemRequest(
                slug,
                "A Plus B",
                "Add two numbers.",
                "1 <= a,b <= 100",
                800,
                1000,
                262144,
                "EXACT",
                null,
                null,
                null, null, null);
    }

    /** A valid request carrying tags, which {@link #validRequest} deliberately omits. */
    private ProblemRequest taggedRequest(
            String slug,
            List<String> tags) {

        return new ProblemRequest(
                slug,
                "Tagged",
                "Statement.",
                null,
                800,
                1000,
                262144,
                "EXACT",
                null,
                null,
                null, tags, null);
    }

    /** A request that is valid in every respect except the unimplemented judge mode. */
    private ProblemRequest specialRequest(
            String slug) {

        return new ProblemRequest(
                slug,
                "Custom Checked",
                "Output any valid answer.",
                null,
                800,
                1000,
                262144,
                "SPECIAL",
                null,
                "int main() { return 0; }",
                null, null, null);
    }

    private ProblemUsageRepository.ManagedProblemRow managedRow(
            String slug, long submissions, long contests, long clarifications) {

        var row = mock(ProblemUsageRepository.ManagedProblemRow.class);
        when(row.getSlug()).thenReturn(slug);
        when(row.getTitle()).thenReturn(slug);
        when(row.getDifficulty()).thenReturn(800);
        when(row.getJudgeMode()).thenReturn("EXACT");
        when(row.getArchived()).thenReturn(false);
        when(row.getTestCases()).thenReturn(3L);
        when(row.getSubmissions()).thenReturn(submissions);
        when(row.getContests()).thenReturn(contests);
        when(row.getClarifications()).thenReturn(clarifications);
        when(row.getInLiveContest()).thenReturn(false);
        return row;
    }

    /** A persisted-looking problem: the id matters because update() compares ids on a slug clash. */
    private Problem problem(String slug) {

        Problem problem =
                new Problem(
                        slug,
                        "A Plus B",
                        "Add two numbers.",
                        "1 <= a,b <= 100",
                        800,
                        1000,
                        262144,
                        "EXACT",
                        null,
                        null,
                        null,
                        5L);

        try {
            var field = Problem.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(problem, 42L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }

        return problem;
    }

    private User user(
            Long id,
            String role) {

        User user = new User();
        user.setId(id);
        user.setRole(role);

        return user;
    }

    private void assertStatus(
            Throwable throwable,
            HttpStatus expected) {

        assertThat(throwable)
                .isInstanceOf(
                        ResponseStatusException.class);

        assertThat(
                ((ResponseStatusException) throwable)
                        .getStatusCode())
                .isEqualTo(expected);
    }
}
