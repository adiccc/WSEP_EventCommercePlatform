package app.init.handlers;

import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.CompanyService;
import application.Response;
import domain.dataType.PermissionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AppointManagerHandler implements InitOperationHandler {

    @Autowired
    private CompanyService companyService;

    @Override
    public String operationType() { return "appoint-manager"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        String ownerToken     = params.get("ownerToken");
        String appointeeToken = params.get("appointeeToken");
        int    companyId      = Integer.parseInt(params.get("companyId"));
        int    appointeeId    = Integer.parseInt(params.get("appointeeId"));
        Set<PermissionType> permissions = Arrays.stream(params.get("permissions").split(","))
                .map(String::trim)
                .map(PermissionType::valueOf)
                .collect(Collectors.toSet());

        Response<Boolean> req = companyService.requestAppointManager(ownerToken, companyId, appointeeId, permissions);
        if (req.getValue() == null)
            throw new InitializationException("appoint-manager request failed: " + req.getMessage());

        Response<Boolean> res = companyService.respondToManagerAppointment(appointeeToken, companyId, true);
        if (res.getValue() == null)
            throw new InitializationException("appoint-manager respond failed: " + res.getMessage());
        return true;
    }
}
