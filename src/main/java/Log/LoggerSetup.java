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
        try
        {
            // Event log
            FileHandler eventHandler = new FileHandler("logs/event-log.json", true);
            eventHandler.setFormatter(new JsonFormatter());
            eventHandler.setLevel(Level.INFO);
            eventHandler.setFilter(record ->
                    record.getLevel() == Level.INFO
            );

            // Error log (WARNING + SEVERE)
            FileHandler errorHandler = new FileHandler("logs/error-log.json", true);
            errorHandler.setFormatter(new JsonFormatter());
            errorHandler.setLevel(Level.WARNING);
            errorHandler.setFilter(record ->
                    record.getLevel().intValue() >= Level.WARNING.intValue()
            );

            rootLogger.addHandler(eventHandler);
            rootLogger.addHandler(errorHandler);
        }catch(IOException e){
            rootLogger.setUseParentHandlers(true);
        }
    }
}