package app.init;

import app.config.SystemProperties;
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
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "system.init-enabled", havingValue = "true", matchIfMissing = true)
public class SystemInitializer implements ApplicationRunner {

    private static final Logger logger = Logger.getLogger(SystemInitializer.class.getName());

    @Autowired private SystemProperties systemProperties;
    @Autowired private ResourceLoader resourceLoader;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private List<InitOperationHandler> handlers;

    private Map<String, InitOperationHandler> handlerMap;

    @PostConstruct
    public void buildHandlerMap() {
        handlerMap = handlers.stream()
                .collect(Collectors.toMap(InitOperationHandler::operationType, h -> h));
        logger.info("Registered init handlers: " + handlerMap.keySet());
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String path = systemProperties.getInitStateFile();
        logger.info("Loading init-state file: " + path);

        InitStateFile initState;
        try {
            var resource = resourceLoader.getResource(path);
            initState = objectMapper.readValue(resource.getInputStream(), InitStateFile.class);
        } catch (Exception e) {
            throw new InitializationException("Failed to load init-state file '" + path + "': " + e.getMessage());
        }

        if (initState.getOperations() == null || initState.getOperations().isEmpty()) {
            logger.info("Init-state file is empty — skipping initialization.");
            return;
        }

        InitContext context = new InitContext();

        for (int i = 0; i < initState.getOperations().size(); i++) {
            InitOperation op = initState.getOperations().get(i);
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

        logger.info("System initialization completed successfully.");
    }
}
