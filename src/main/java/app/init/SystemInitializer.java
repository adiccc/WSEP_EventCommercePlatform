package app.init;

import app.config.SystemProperties;
import application.IPaymentSystem;
import application.ITicketSupply;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    @PostConstruct
    public void buildHandlerMap() {
        handlerMap = handlers.stream()
                .collect(Collectors.toMap(InitOperationHandler::operationType, h -> h));
        logger.info("Registered init handlers: " + handlerMap.keySet());
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
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
            throw new InitializationException("Failed to load init-state file '" + path + "': " + e.getMessage());
        }

        if (initState.getOperations() == null)
            throw new InitializationException("Init-state file must contain an 'operations' array.");

        Set<String> registeredEmails = initState.getOperations().stream()
                .filter(op -> "register".equals(op.getType()) && op.getParams() != null)
                .map(op -> op.getParams().get("email"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (String adminEmail : systemProperties.getAdminEmails()) {
            if (!registeredEmails.contains(adminEmail))
                throw new InitializationException(
                        "Admin email '" + adminEmail + "' must have a 'register' operation in the init-state file.");
        }

        InitContext context = new InitContext();

        for (int i = 0; i < initState.getOperations().size(); i++) {
            InitOperation op = initState.getOperations().get(i);
            // Every init operation must declare its type, so it can be matched to a handler.
            if (op.getType() == null || op.getType().isBlank()) {
                throw new InitializationException("Init operation type must not be blank");
            }
            logger.info("Init step [" + (i + 1) + "/" + initState.getOperations().size() + "]: " + op.getType());

            InitOperationHandler handler = handlerMap.get(op.getType());
            if (handler == null)
                throw new InitializationException("Unknown operation type: '" + op.getType() + "'");

            Map<String, String> resolvedParams = context.resolveParams(op.getParams());
            Object result = handler.execute(resolvedParams, context);

            if (op.getStore() != null && !op.getStore().isBlank()) {
                context.store(op.getStore(), result);
                logger.info("Stored result as '${" + op.getStore() + "}'");
            }
        }
        logger.info("Performing handshake with external systems...");

        boolean isPaymentSystemUp = paymentSystem.handshake();
        if (!isPaymentSystemUp) {
            throw new InitializationException("Failed to initialize: Payment system is unreachable.");
        }

        boolean isTicketSystemUp = ticketSupply.handshake();
        if (!isTicketSystemUp) {
            throw new InitializationException("Failed to initialize: Ticket supply system is unreachable.");
        }

        logger.info("External systems handshake successful!");
        logger.info("System initialization completed successfully.");
    }

}
