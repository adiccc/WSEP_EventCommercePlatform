package app.init.handlers;

import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.CompanyService;
import application.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AppointOwnerHandler implements InitOperationHandler {

    @Autowired
    private CompanyService companyService;

    @Override
    public String operationType() { return "appoint-owner"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        String ownerToken     = params.get("ownerToken");
        String appointeeToken = params.get("appointeeToken");
        int    companyId      = Integer.parseInt(params.get("companyId"));
        int    appointeeId    = Integer.parseInt(params.get("appointeeId"));

        Response<Boolean> req = companyService.requestAppointOwner(ownerToken, companyId, appointeeId);
        if (req.getValue() == null)
            throw new InitializationException("appoint-owner request failed: " + req.getMessage());

        Response<Boolean> res = companyService.respondToOwnerAppointment(appointeeToken, companyId, true);
        if (res.getValue() == null)
            throw new InitializationException("appoint-owner respond failed: " + res.getMessage());
        return true;
    }
}
