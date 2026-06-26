package Log;
import java.io.IOException;
import java.util.logging.*;

public class LoggerSetup {

    public static void setup()  {
        Logger rootLogger = Logger.getLogger("");

        for (Handler h : rootLogger.getHandlers()) {
            rootLogger.removeHandler(h);
        }

        rootLogger.setLevel(Level.ALL);
        rootLogger.setUseParentHandlers(false);

        // Console output: makes INFO/WARNING/SEVERE visible on screen (in addition to the log files).
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        rootLogger.addHandler(consoleHandler);

        try
        {
            // Event log
            FileHandler eventHandler = new FileHandler("logs/event-log.jsonl", true);
            eventHandler.setFormatter(new JsonFormatter());
            eventHandler.setLevel(Level.INFO);
            eventHandler.setFilter(record ->
                    record.getLevel() == Level.INFO
            );

            // Error log (WARNING + SEVERE)
            FileHandler errorHandler = new FileHandler("logs/error-log.jsonl", true);
            errorHandler.setFormatter(new JsonFormatter());
            errorHandler.setLevel(Level.WARNING);
            errorHandler.setFilter(record ->
                    record.getLevel().intValue() >= Level.WARNING.intValue()
            );

            rootLogger.addHandler(eventHandler);
            rootLogger.addHandler(errorHandler);
        }catch(IOException e){
            // File logging unavailable; the console handler above still provides visible output.
            rootLogger.log(Level.WARNING, "Could not initialize file log handlers: " + e.getMessage());
        }
    }
}