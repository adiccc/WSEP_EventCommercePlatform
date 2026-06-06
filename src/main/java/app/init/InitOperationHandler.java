package app.init;

import java.util.Map;

public interface InitOperationHandler {

    String operationType();

    Object execute(Map<String, String> params, InitContext context);
}
