package app.init;

import app.config.SystemProperties;
import application.IPaymentSystem;
import application.ITicketSupply;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "system.init-enabled", havingValue = "true", matchIfMissing = true)
public class SystemInitializer implements ApplicationRunner {

    private static final Logger logger = Logger.getLogger(SystemInitializer.class.getName());

    @Autowired
    private SystemProperties systemProperties;
    @Autowired
    private ResourceLoader resourceLoader;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private List<InitOperationHandler> handlers;

    private Map<String, InitOperationHandler> handlerMap;

    @Autowired
    private IPaymentSystem paymentSystem;

    @Autowired
    private ITicketSupply ticketSupply;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @PostConstruct
    public void buildHandlerMap() {
        handlerMap = handlers.stream()
                .collect(Collectors.toMap(InitOperationHandler::operationType, h -> h));
        logger.info("Registered init handlers: " + handlerMap.keySet());
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String path;
            boolean emptyDb = args.containsOption("db") && args.getOptionValues("db").contains("empty");
            if (!emptyDb) {
                return;
            }
            if (args.containsOption("init-file")) {
                String custom = args.getOptionValues("init-file").get(0);
                path = (custom.startsWith("classpath:") || custom.startsWith("file:"))
                        ? custom
                        : "file:" + custom;
                logger.info("Using custom init-state file from --init-file: " + path);
            } else {
                path = systemProperties.getInitStateFile();
            }

            logger.info("Loading init-state file: " + path);

            InitStateFile initState;
            try {
                var resource = resourceLoader.getResource(path);
                initState = objectMapper.readValue(resource.getInputStream(), InitStateFile.class);
            } catch (Exception e) {
                abortStartup("Could not read the init-state file '" + path + "': " + e.getMessage()
                        + ". The system needs this file to load its initial data, so it cannot start.");
                return;
            }

            if (initState.getOperations() == null) {
                abortStartup("The init-state file '" + path + "' has no 'operations' array. "
                        + "Without operations there is nothing to initialize, so the system cannot start.");
                return;
            }

            Set<String> registeredEmails = initState.getOperations().stream()
                    .filter(op -> "register".equals(op.getType()) && op.getParams() != null)
                    .map(op -> op.getParams().get("email"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            for (String adminEmail : systemProperties.getAdminEmails()) {
                if (!registeredEmails.contains(adminEmail)) {
                    abortStartup("Admin email '" + adminEmail + "' is configured but has no 'register' operation "
                            + "in the init-state file. The system requires the admin account to exist, so it cannot start.");
                    return;
                }
            }

            InitContext context = new InitContext();

            for (int i = 0; i < initState.getOperations().size(); i++) {
                InitOperation op = initState.getOperations().get(i);
                int stepNumber = i + 1;
                int totalSteps = initState.getOperations().size();

                // Every init operation must declare its type, so it can be matched to a handler.
                if (op.getType() == null || op.getType().isBlank()) {
                    abortStartup("Init step " + stepNumber + "/" + totalSteps + " has a blank operation type. "
                            + "Every operation must declare a 'type', so the system cannot start.");
                    return;
                }
                logger.info("Init step [" + stepNumber + "/" + totalSteps + "]: " + op.getType());

                InitOperationHandler handler = handlerMap.get(op.getType());
                if (handler == null) {
                    abortStartup("Init step " + stepNumber + "/" + totalSteps + " uses unknown operation type '"
                            + op.getType() + "'. Known types are " + handlerMap.keySet()
                            + ". The system cannot start until this is fixed.");
                    return;
                }

                try {
                    Map<String, String> resolvedParams = context.resolveParams(op.getParams());
                    Object result = handler.execute(resolvedParams, context);

                    if (op.getStore() != null && !op.getStore().isBlank()) {
                        context.store(op.getStore(), result);
                        logger.info("Stored result as '${" + op.getStore() + "}'");
                    }
                } catch (DataAccessException | CannotCreateTransactionException e) {
                    logger.log(Level.SEVERE, "Init step " + stepNumber + "/" + totalSteps
                            + " ('" + op.getType() + "') failed: database unavailable", e);
                    abortStartup("Init step " + stepNumber + "/" + totalSteps + " ('" + op.getType() + "') "
                            + "could not reach the database. Make sure the database is running and reachable. "
                            + "The system cannot start.");
                    return;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Init step " + stepNumber + "/" + totalSteps
                            + " ('" + op.getType() + "') failed", e);
                    abortStartup("Init step " + stepNumber + "/" + totalSteps + " ('" + op.getType() + "') failed: "
                            + e.getMessage() + ". The system cannot start because this step did not complete.");
                    return;
                }
            }
            logger.info("Performing handshake with external systems...");

            boolean isPaymentSystemUp = paymentSystem.handshake();
            if (!isPaymentSystemUp) {
                abortStartup("The payment system is unreachable. The system cannot start without a working "
                        + "payment integration.");
                return;
            }

            boolean isTicketSystemUp = ticketSupply.handshake();
            if (!isTicketSystemUp) {
                abortStartup("The ticket supply system is unreachable. The system cannot start without a working "
                        + "ticket supply integration.");
                return;
            }

            logger.info("External systems handshake successful!");
            logger.info("System initialization completed successfully.");
        } catch (NullPointerException e) {
            logger.log(Level.SEVERE, "Initialization failed: missing required data (NullPointerException)", e);
            abortStartup("A required piece of initialization data was missing "
                    + "(such as the init-state file path, the admin-emails configuration, or the handler registry). "
                    + "The system cannot start.");
        } catch (IndexOutOfBoundsException e) {
            logger.log(Level.SEVERE, "Initialization failed: command-line option without a value (IndexOutOfBoundsException)", e);
            abortStartup("A command-line option (such as --init-file) was given without a value. "
                    + "Please provide a value for it. The system cannot start.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Initialization failed: unexpected error", e);
            abortStartup("An unexpected problem occurred while initializing the system. "
                    + "The system cannot start.");
        }
    }


    /**
     * Logs a clear explanation of why initialization failed and shuts the application down cleanly.
     * Used instead of throwing so the operator sees a readable message instead of a stack trace.
     * Package-private and overridable so tests can assert on the failure reason without exiting the JVM.
     */
    void abortStartup(String reason) {
        logger.severe("==================== SYSTEM INITIALIZATION FAILED ====================");
        logger.severe(reason);
        logger.severe("The system is going to shut down now and will not start.");
        logger.severe("=====================================================================");
        int exitCode = SpringApplication.exit(applicationContext, () -> 1);
        System.exit(exitCode);
    }

}
