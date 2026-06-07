package app.init;

import java.util.HashMap;
import java.util.Map;

public class InitOperation {

    private String type;
    private Map<String, String> params = new HashMap<>();
    private String store;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) { this.params = params; }

    public String getStore() { return store; }
    public void setStore(String store) { this.store = store; }
}
