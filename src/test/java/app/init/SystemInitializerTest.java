package app.init;

import app.config.SystemProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SystemInitializerTest {

    @TempDir
    Path tempDir;

    private SystemInitializer initializer;
    private SystemProperties systemProperties;

    @BeforeEach
    void setUp() {
        initializer = new SystemInitializer();
        systemProperties = mock(SystemProperties.class);
        when(systemProperties.getAdminEmails()).thenReturn(List.of("admin@test.com"));
        when(systemProperties.getInitStateFile()).thenReturn("file:/nonexistent-default.json");

        ReflectionTestUtils.setField(initializer, "systemProperties", systemProperties);
        ReflectionTestUtils.setField(initializer, "resourceLoader", new DefaultResourceLoader());
        ReflectionTestUtils.setField(initializer, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(initializer, "handlers", List.of());
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

    // ── Skip tests ────────────────────────────────────────────────────────────

    @Test
    void GivenNoDbEmptyArg_WhenRun_ThenSkipsInitWithoutError() throws Exception {
        initializer.run(new DefaultApplicationArguments());
    }

    // ── File loading ──────────────────────────────────────────────────────────

    @Test
    void GivenNonExistentInitFile_WhenRun_ThenThrowsInitializationException() {
        assertThrows(InitializationException.class,
                () -> initializer.run(new DefaultApplicationArguments("--db=empty")));
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

        assertDoesNotThrow(() -> initializer.run(emptyDbArgs(custom.toString())));
    }

    // ── JSON format ───────────────────────────────────────────────────────────

    @Test
    void GivenMalformedJson_WhenRun_ThenThrowsInitializationException() throws Exception {
        Path file = writeInitFile("{ not valid json }");
        assertThrows(InitializationException.class, () -> initializer.run(emptyDbArgs(file.toString())));
    }

    @Test
    void GivenNullOperations_WhenRun_ThenThrowsInitializationException() throws Exception {
        Path file = writeInitFile("{\"operations\": null}");
        assertThrows(InitializationException.class, () -> initializer.run(emptyDbArgs(file.toString())));
    }

    @Test
    void GivenBlankOperationType_WhenRun_ThenThrowsInitializationException() throws Exception {
        Path file = writeInitFile("{\"operations\":[{\"type\":\"\",\"params\":{\"email\":\"admin@test.com\"}}]}");
        assertThrows(InitializationException.class, () -> initializer.run(emptyDbArgs(file.toString())));
    }

    @Test
    void GivenUnknownOperationType_WhenRun_ThenThrowsInitializationException() throws Exception {
        Path file = writeInitFile("{\"operations\":[{\"type\":\"no-such-op\",\"params\":{\"email\":\"admin@test.com\"}}]}");
        assertThrows(InitializationException.class, () -> initializer.run(emptyDbArgs(file.toString())));
    }

    // ── Admin validation ──────────────────────────────────────────────────────

    @Test
    void GivenAdminEmailMissingFromInitFile_WhenRun_ThenThrowsInitializationException() throws Exception {
        Path file = writeInitFile("{\"operations\":[{\"type\":\"register\",\"params\":{\"email\":\"other@test.com\"}}]}");
        assertThrows(InitializationException.class, () -> initializer.run(emptyDbArgs(file.toString())));
    }

    @Test
    void GivenAllAdminEmailsRegistered_WhenRun_ThenSucceeds() throws Exception {
        Path file = writeInitFile("{\"operations\":[{\"type\":\"register\",\"params\":{\"email\":\"admin@test.com\"}}]}");
        InitOperationHandler handler = mock(InitOperationHandler.class);
        when(handler.operationType()).thenReturn("register");
        when(handler.execute(any(), any())).thenReturn("admin@test.com");
        ReflectionTestUtils.setField(initializer, "handlers", List.of(handler));
        initializer.buildHandlerMap();

        assertDoesNotThrow(() -> initializer.run(emptyDbArgs(file.toString())));
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
}
