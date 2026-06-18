package app.init.handlers;

import DTO.DiscountDTO;
import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.CompanyService;
import application.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class AddCompanyDiscountHandler implements InitOperationHandler {

    @Autowired
    private CompanyService companyService;

    @Override
    public String operationType() { return "add-company-discount"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        String token     = params.get("token");
        int    companyId = Integer.parseInt(params.get("companyId"));
        String code      = params.get("couponCode");
        double percent   = Double.parseDouble(params.get("percent"));
        LocalDate expiry = LocalDate.now().plusDays(Long.parseLong(params.get("expiryDaysFromNow")));

        Response<Boolean> response = companyService.addDiscountToCompany(
                token, companyId, new DiscountDTO(code, percent, expiry));
        if (response.getValue() == null)
            throw new InitializationException("add-company-discount failed: " + response.getMessage());
        return true;
    }
}
