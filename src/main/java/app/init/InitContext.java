package app.init;

import java.util.HashMap;
import java.util.Map;

public class InitContext {

    private final Map<String, Object> variables = new HashMap<>();

    public void store(String name, Object value) {
        variables.put(name, value);
    }

    public String resolve(String value) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String varName = value.substring(2, value.length() - 1);
            Object resolved = variables.get(varName);
            if (resolved == null)
                throw new InitializationException("Undefined variable: " + varName);
            return resolved.toString();
        }
        return value;
    }

    public Map<String, String> resolveParams(Map<String, String> params) {
        Map<String, String> resolved = new HashMap<>();
        params.forEach((k, v) -> resolved.put(k, resolve(v)));
        return resolved;
    }
}
