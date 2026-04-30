package Log;
import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class JsonFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
        return "{"
                + "\"timestamp\":\"" + Instant.ofEpochMilli(record.getMillis()) + "\","
                + "\"level\":\"" + record.getLevel() + "\","
                + "\"logger\":\"" + record.getLoggerName() + "\","
                + "\"message\":\"" + escape(record.getMessage()) + "\""
                + "}\n";
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}