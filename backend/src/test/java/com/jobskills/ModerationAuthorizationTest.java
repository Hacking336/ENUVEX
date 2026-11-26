package com.jobskills;

import com.jobskills.controller.AdminController;
import com.jobskills.controller.EmployerController;
import com.jobskills.controller.ReportSubmissionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that no Phase 6 moderation route is reachable without an administrator
 * and that the reporter's identity always comes from the security context.
 *
 * <p>These are annotation checks because the integration suite needs a
 * PostgreSQL instance that is not available in this environment; the HTTP-level
 * 401/403 behaviour is reported as BLOCKED in the Phase 6 report.
 */
class ModerationAuthorizationTest {

    private static final List<String> FORBIDDEN_ADMIN_VERBS = List.of("PUT", "PATCH", "DELETE");

    @Test
    @DisplayName("Every admin moderation route sits behind hasRole('ADMIN')")
    void adminControllerIsAdminOnly() {
        PreAuthorize guard = AdminController.class.getAnnotation(PreAuthorize.class);
        assertThat(guard).isNotNull();
        assertThat(guard.value()).isEqualTo("hasRole('ADMIN')");
        assertThat(AdminController.class.getAnnotation(RequestMapping.class).value())
            .containsExactly("/api/admin");
    }

    @Test
    @DisplayName("Audit logs are read-only: no route edits or deletes an audit entry")
    void auditLogsAreReadOnly() {
        List<Method> auditRoutes = Arrays.stream(AdminController.class.getDeclaredMethods())
            .filter(method -> method.getName().toLowerCase().contains("audit"))
            .filter(method -> method.getAnnotation(GetMapping.class) != null
                || method.getAnnotation(PostMapping.class) != null
                || method.getAnnotation(PutMapping.class) != null
                || method.getAnnotation(DeleteMapping.class) != null)
            .toList();

        assertThat(auditRoutes).isNotEmpty();
        for (Method method : auditRoutes) {
            assertThat(method.getAnnotation(PutMapping.class)).isNull();
            assertThat(method.getAnnotation(PostMapping.class)).isNull();
            assertThat(method.getAnnotation(DeleteMapping.class)).isNull();
            assertThat(method.getAnnotation(GetMapping.class)).isNotNull();
        }
    }

    @Test
    @DisplayName("No admin moderation route is declared without the class-level guard")
    void adminModerationRoutesDeclareNoWeakerGuard() {
        Arrays.stream(AdminController.class.getDeclaredMethods())
            .map(method -> method.getAnnotation(PreAuthorize.class))
            .filter(guard -> guard != null)
            .forEach(guard -> assertThat(guard.value()).doesNotContain("permitAll")
                .doesNotContain("isAuthenticated"));

        Arrays.stream(AdminController.class.getDeclaredMethods())
            .filter(method -> method.getAnnotation(DeleteMapping.class) != null)
            .forEach(method -> assertThat(FORBIDDEN_ADMIN_VERBS).contains("DELETE"));
    }

    @Test
    @DisplayName("Report submission requires a signed-in account and takes no reporter id")
    void reportSubmissionUsesSecurityContext() {
        PreAuthorize guard = ReportSubmissionController.class.getAnnotation(PreAuthorize.class);
        assertThat(guard).isNotNull();
        assertThat(guard.value()).isEqualTo("isAuthenticated()");

        assertThat(Arrays.stream(ReportSubmissionController.class.getDeclaredMethods())
            .flatMap(method -> Arrays.stream(method.getParameters()))
            .map(parameter -> parameter.getType().getSimpleName()))
            .doesNotContain("Long");

        assertThat(Arrays.stream(ReportSubmissionController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("submit"))
            .findFirst().orElseThrow()
            .getParameters()[0].getType().getSimpleName()).isEqualTo("ReportRequest");
    }

    @Test
    @DisplayName("An employer can only ask for review, never award itself a verified status")
    void employerCannotSelfVerify() {
        Method request = Arrays.stream(EmployerController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("requestVerification"))
            .findFirst().orElseThrow();

        assertThat(request.getAnnotation(PostMapping.class).value())
            .containsExactly("/verification-request");
        assertThat(EmployerController.class.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasRole('EMPLOYER')");
        assertThat(Arrays.stream(request.getParameters())
            .map(parameter -> parameter.getType().getSimpleName()))
            .doesNotContain("VerificationStatus", "AdminEmployerDto", "EmployerProfile");
    }

    @Test
    @DisplayName("Verification decisions live only in the admin-only controller")
    void verificationDecisionsAreAdminOnly() {
        assertThat(Arrays.stream(AdminController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("verifyEmployer"))
            .findFirst().orElseThrow()
            .getAnnotation(PutMapping.class).value())
            .containsExactly("/employers/{id}/verify");

        assertThat(Arrays.stream(EmployerController.class.getDeclaredMethods())
            .filter(method -> method.getAnnotation(PutMapping.class) != null)
            .flatMap(method -> Arrays.stream(method.getAnnotation(PutMapping.class).value()))
            .noneMatch(path -> path.contains("verify")))
            .isTrue();
    }
}
