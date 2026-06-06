package app.init.handlers;

import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.CompanyService;
import application.Response;
import domain.company.Company;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpenCompanyHandler implements InitOperationHandler {

    @Autowired
    private CompanyService companyService;

    @Override
    public String operationType() { return "open-company"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        Response<Company> response = companyService.createProductionCompany(
                params.get("token"),
                Integer.parseInt(params.get("companyId")),
                params.get("companyName"),
                params.get("email"),
                params.get("phone"),
                params.get("bankAccount")
        );
        if (response.isError())
            throw new InitializationException("open-company failed: " + response.getMessage());
        return response.getValue().getCompanyId();
    }
}
