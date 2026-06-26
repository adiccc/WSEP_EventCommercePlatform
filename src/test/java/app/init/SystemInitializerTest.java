package app.init;

import app.config.SystemProperties;
import application.IPaymentSystem;
import application.ITicketSupply;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SystemInitializerTest {

    @TempDir
    Path tempDir;

    private SystemInitializer initializer;
    private SystemProperties systemProperties;
    private IPaymentSystem paymentSystem;
    private ITicketSupply ticketSupply;

    @BeforeEach
    void setUp() {
        // Spy so we can stub abortStartup() and avoid it shutting down the test JVM via System.exit.
        initializer = spy(new SystemInitializer());
        doNothing().when(initializer).abortStartup(anyString());

        systemProperties = mock(SystemProperties.class);
        when(systemProperties.getAdminEmails()).thenReturn(List.of("admin@test.com"));
        when(systemProperties.getInitStateFile()).thenReturn("file:/nonexistent-default.json");

        ReflectionTestUtils.setField(initializer, "systemProperties", systemProperties);
        ReflectionTestUtils.setField(initializer, "resourceLoader", new DefaultResourceLoader());
        ReflectionTestUtils.setField(initializer, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(initializer, "handlers", List.of());

        paymentSystem = mock(IPaymentSystem.class);
        ticketSupply = mock(ITicketSupply.class);

        when(paymentSystem.handshake()).thenReturn(true);
        when(ticketSupply.handshake()).thenReturn(true);

        ReflectionTestUtils.setField(initializer, "paymentSystem", paymentSystem);
        ReflectionTestUtils.setField(initializer, "ticketSupply", ticketSupply);

        initializer.buildHandlerMap();
    }

    private DefaultApplicationArguments emptyDbArgs(String initFilePath) {
        return new DefaultApplicationArguments("--db=empty", "--init-file=" + initFilePath);
    }

    private Path writeInitFile(String json) throws Exception {
        Path file = tempDir.resolve("init.json");
        Files.writeString(file, json);
        return file;
    }

    /** Runs the initializer and returns the reason passed to abortStartup (fails the test if it was never called). */
    private String captureAbortReason() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(initializer).abortStartup(captor.capture());
        return captor.getValue();
    }

    // ── Skip tests ────────────────────────────────────────────────────────────

    @Test
    void GivenNoDbEmptyArg_WhenRun_ThenSkipsInitWithoutAborting() throws Exception {
        initializer.run(new DefaultApplicationArguments());
        verify(initializer, never()).abortStartup(anyString());
    }

    // ── File loading ──────────────────────────────────────────────────────────

    @Test
    void GivenNonExistentInitFile_WhenRun_ThenAbortsWithReadFailureMessage() throws Exception {
        initializer.run(new DefaultApplicationArguments("--db=empty"));
        assertTrue(captureAbortReason().contains("Could not read the init-state file"));
    }

    @Test
    void GivenInitFileArg_WhenRun_ThenUsesCustomFileInsteadOfDefault() throws Exception {
        // Default is non-existent; custom file is valid and registers the admin
        Path custom = writeInitFile("{\"operations\":[{\"type\":\"register\",\"params\":{\"email\":\"admin@test.com\"}}]}");
        InitOperationHandler handler = mock(InitOperationHandler.class);
        when(handler.operationType()).thenReturn("register");
        when(handler.execute(any(), any())).thenReturn("admin@test.com");
        ReflectionTestUtils.setField(initializer, "handlers", List.of(handler));
        initializer.buildHandlerMap();

        initializer.run(emptyDbArgs(custom.toString()));
        verify(initializer, never()).abortStartup(anyString());
    }

    // ── JSON format ───────────────────────────────────────────────────────────

    @Test
    void GivenMalformedJson_WhenRun_ThenAbortsWithReadFailureMessage() throws Exception {
        Path file = writeInitFile("{ not valid json }");
        initializer.run(emptyDbArgs(file.toString()));
        assertTrue(captureAbortReason().contains("Could not read the init-state file"));
    }

    @Test
    void GivenNullOperations_WhenRun_ThenAbortsWithMissingOperationsMessage() throws Exception {
        Path file = writeInitFile("{\"operations\": null}");
        initializer.run(emptyDbArgs(file.toString()));
        assertTrue(captureAbortReason().contains("no 'operations' array"));
    }

    @Test
    void GivenBlankOperationType_WhenRun_ThenAbortsWithBlankTypeMessage() throws Exception {
        // No admin emails configured, so execution reaches the per-operation validation.
        when(systemProperties.getAdminEmails()).thenReturn(List.of());
        Path file = writeInitFile("{\"operations\":[{\"type\":\"\",\"params\":{}}]}");
        initializer.run(emptyDbArgs(file.toString()));
        assertTrue(captureAbortReason().contains("blank operation type"));
    }

    @Test
    void GivenUnknownOperationType_WhenRun_ThenAbortsWithUnknownTypeMessage() throws Exception {
        // No admin emails configured, so execution reaches the per-operation validation.
        when(systemProperties.getAdminEmails()).thenReturn(List.of());
        Path file = writeInitFile("{\"operations\":[{\"type\":\"no-such-op\",\"params\":{}}]}");
        initializer.run(emptyDbArgs(file.toString()));
        assertTrue(captureAbortReason().contains("unknown operation type 'no-such-op'"));
    }

    // ── Admin validation ──────────────────────────────────────────────────────

    @Test
    void GivenAdminEmailMissingFromInitFile_WhenRun_ThenAbortsWithAdminMessage() throws Exception {
        Path file = writeInitFile("{\"operations\":[{\"type\":\"register\",\"params\":{\"email\":\"other@test.com\"}}]}");
        initializer.run(emptyDbArgs(file.toString()));
        String reason = captureAbortReason();
        assertTrue(reason.contains("admin@test.com"));
        assertTrue(reason.contains("no 'register' operation"));
    }

    @Test
    void GivenAllAdminEmailsRegistered_WhenRun_ThenSucceedsWithoutAborting() throws Exception {
        Path file = writeInitFile("{\"operations\":[{\"type\":\"register\",\"params\":{\"email\":\"admin@test.com\"}}]}");
        InitOperationHandler handler = mock(InitOperationHandler.class);
        when(handler.operationType()).thenReturn("register");
        when(handler.execute(any(), any())).thenReturn("admin@test.com");
        ReflectionTestUtils.setField(initializer, "handlers", List.of(handler));
        initializer.buildHandlerMap();

        initializer.run(emptyDbArgs(file.toString()));
        verify(initializer, never()).abortStartup(anyString());
    }

    // ── Handler failure ───────────────────────────────────────────────────────

    @Test
    void GivenHandlerThrows_WhenRun_ThenAbortsWithStepFailureMessage() throws Exception {
        Path file = writeInitFile("{\"operations\":[{\"type\":\"register\",\"params\":{\"email\":\"admin@test.com\"}}]}");
        InitOperationHandler handler = mock(InitOperationHandler.class);
        when(handler.operationType()).thenReturn("register");
        when(handler.execute(any(), any())).thenThrow(new InitializationException("register failed: boom"));
        ReflectionTestUtils.setField(initializer, "handlers", List.of(handler));
        initializer.buildHandlerMap();

        initializer.run(emptyDbArgs(file.toString()));
        String reason = captureAbortReason();
        assertTrue(reason.contains("('register') failed"));
        assertTrue(reason.contains("register failed: boom"));
    }

    // ── Default init-state.json regression ───────────────────────────────────

    @Test
    void GivenDefaultInitStateJson_ThenAllConfiguredAdminEmailsAreRegistered() throws Exception {
        var resource = new DefaultResourceLoader().getResource("classpath:init-state.json");
        var initState = new ObjectMapper().readValue(resource.getInputStream(), InitStateFile.class);

        Set<String> registeredEmails = initState.getOperations().stream()
                .filter(op -> "register".equals(op.getType()) && op.getParams() != null)
                .map(op -> op.getParams().get("email"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<String> adminEmails = List.of("systemadmin@demo.com");
        for (String adminEmail : adminEmails) {
            assertTrue(registeredEmails.contains(adminEmail),
                    "Admin email '" + adminEmail + "' is missing a 'register' operation in init-state.json");
        }
    }

    // ── External Systems Validation ──────────────────────────────────────────

    @Test
    void GivenPaymentSystemHandshakeFails_WhenRun_ThenAbortsWithPaymentMessage() throws Exception {
        Path file = writeInitFile("{\"operations\":[{\"type\":\"register\",\"params\":{\"email\":\"admin@test.com\"}}]}");
        InitOperationHandler handler = mock(InitOperationHandler.class);
        when(handler.operationType()).thenReturn("register");
        when(handler.execute(any(), any())).thenReturn("admin@test.com");
        ReflectionTestUtils.setField(initializer, "handlers", List.of(handler));
        initializer.buildHandlerMap();

        when(paymentSystem.handshake()).thenReturn(false);

        initializer.run(emptyDbArgs(file.toString()));
        assertTrue(captureAbortReason().contains("payment system is unreachable"));
    }

    @Test
    void GivenTicketSupplyHandshakeFails_WhenRun_ThenAbortsWithTicketMessage() throws Exception {
        Path file = writeInitFile("{\"operations\":[{\"type\":\"register\",\"params\":{\"email\":\"admin@test.com\"}}]}");
        InitOperationHandler handler = mock(InitOperationHandler.class);
        when(handler.operationType()).thenReturn("register");
        when(handler.execute(any(), any())).thenReturn("admin@test.com");
        ReflectionTestUtils.setField(initializer, "handlers", List.of(handler));
        initializer.buildHandlerMap();

        when(ticketSupply.handshake()).thenReturn(false);

        initializer.run(emptyDbArgs(file.toString()));
        assertTrue(captureAbortReason().contains("ticket supply system is unreachable"));
    }

    @Test
    void GivenPaymentHandshakeFails_WhenRun_ThenTicketHandshakeIsNeverCalled() throws Exception {
        Path file = writeInitFile("{\"operations\":[{\"type\":\"register\",\"params\":{\"email\":\"admin@test.com\"}}]}");
        InitOperationHandler handler = mock(InitOperationHandler.class);
        when(handler.operationType()).thenReturn("register");
        when(handler.execute(any(), any())).thenReturn("admin@test.com");
        ReflectionTestUtils.setField(initializer, "handlers", List.of(handler));
        initializer.buildHandlerMap();

        when(paymentSystem.handshake()).thenReturn(false);

        initializer.run(emptyDbArgs(file.toString()));

        verify(paymentSystem, times(1)).handshake();
        verify(ticketSupply, never()).handshake();
    }

    @Test
    void GivenTicketSystemThrowsUnexpectedException_WhenRun_ThenExceptionBubblesUp() throws Exception {
        Path file = writeInitFile("{\"operations\":[{\"type\":\"register\",\"params\":{\"email\":\"admin@test.com\"}}]}");
        InitOperationHandler handler = mock(InitOperationHandler.class);
        when(handler.operationType()).thenReturn("register");
        when(handler.execute(any(), any())).thenReturn("admin@test.com");
        ReflectionTestUtils.setField(initializer, "handlers", List.of(handler));
        initializer.buildHandlerMap();

        when(paymentSystem.handshake()).thenReturn(true);
        when(ticketSupply.handshake()).thenThrow(new RuntimeException("Unexpected Core Failure"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> initializer.run(emptyDbArgs(file.toString())));

        assertEquals("Unexpected Core Failure", ex.getMessage());
    }
}
