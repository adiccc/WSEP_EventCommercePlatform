package app.init.handlers;

import DTO.PurchaseRuleDTO;
import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.CompanyService;
import application.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AddCompanyRuleHandler implements InitOperationHandler {

    @Autowired
    private CompanyService companyService;

    @Override
    public String operationType() { return "add-company-rule"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        String token     = params.get("token");
        int    companyId = Integer.parseInt(params.get("companyId"));
        PurchaseRuleDTO.Type ruleType = PurchaseRuleDTO.Type.valueOf(params.get("ruleType"));
        int value = Integer.parseInt(params.get("value"));

        Response<Boolean> response = companyService.addRuleToCompany(token, companyId, new PurchaseRuleDTO(ruleType, value));
        if (response.getValue() == null)
            throw new InitializationException("add-company-rule failed: " + response.getMessage());
        return true;
    }
}
