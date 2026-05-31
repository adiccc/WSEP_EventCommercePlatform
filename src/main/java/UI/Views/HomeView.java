package UI.Views;

import UI.Presenters.HomePresenter;
import application.CompanyService;
import application.IAuth;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.CompanyDTO;
import com.vaadin.flow.component.UI;
import java.util.List;

@Route(value = "home", layout = MainLayout.class)
@PageTitle("Home — EventCommerce")
@AnonymousAllowed
public class HomeView extends VerticalLayout {

    private final HomePresenter presenter;
    private final IAuth auth;
    private final FlexLayout cardsContainer;
    private List<CompanyDTO> allCompanies;

    // One color per initial letter (cycles through 6 colors)
    private static final String[] AVATAR_COLORS = {
        "#4A90D9", "#E67E22", "#27AE60", "#8E44AD", "#E74C3C", "#16A085"
    };

    public HomeView(CompanyService companyService, IAuth auth) {
        this.presenter = new HomePresenter(companyService);
        this.auth = auth;

        setPadding(true);
        setSpacing(true);

        H2 title = new H2("Production Companies");
        title.getStyle().set("margin-bottom", "0");

        Paragraph subtitle = new Paragraph("Browse all event companies and their upcoming events.");
        subtitle.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "0");

        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);

        HorizontalLayout topBar = new HorizontalLayout();
        topBar.setWidthFull();
        topBar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        TextField searchField = new TextField();
        searchField.setPlaceholder("Filter by company name...");
        searchField.setWidth("20rem");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> filterCards(e.getValue()));
        topBar.add(searchField);
        topBar.setFlexGrow(1, searchField);

        if (Boolean.TRUE.equals(auth.isAdmin(token).getValue())) {
            Button adminBtn = new Button("🔧 Admin Panel",
                    e -> UI.getCurrent().navigate("admin"));
            adminBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
            topBar.add(adminBtn);
        }

        cardsContainer = new FlexLayout();
        cardsContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        cardsContainer.getStyle().set("gap", "1.25rem").set("padding", "0.5rem 0");

        add(title, subtitle, topBar, cardsContainer);
        loadCompanies();
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadCompanies() {
        String tabId = UI.getCurrent().getElement().getProperty("currentTabId");
        String token = (String) VaadinSession.getCurrent().getAttribute("token_" + tabId);        List<CompanyDTO> companies = presenter.getCompanies(token);

        if (companies != null) {
            allCompanies = companies;
            renderCards(allCompanies);
        } else {
            // Empty system — show friendly empty state instead of error text
            cardsContainer.add(buildEmptyState());
        }
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private void filterCards(String query) {
        if (allCompanies == null) return;
        if (query == null || query.isBlank()) {
            renderCards(allCompanies);
        } else {
            String lower = query.toLowerCase();
            renderCards(allCompanies.stream()
                    .filter(c -> c.getCompanyName().toLowerCase().contains(lower))
                    .toList());
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void renderCards(List<CompanyDTO> companies) {
        cardsContainer.removeAll();
        if (companies.isEmpty()) {
            cardsContainer.add(buildEmptyState());
            return;
        }
        for (CompanyDTO company : companies) {
            cardsContainer.add(buildCard(company));
        }
    }

    private Div buildEmptyState() {
        Div empty = new Div();
        empty.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("padding", "2rem 0");
        empty.setText("No companies found.");
        return empty;
    }

    private Div buildCard(CompanyDTO company) {
        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "1.25rem")
                .set("width", "240px")
                .set("cursor", "pointer")
                .set("transition", "box-shadow 0.15s ease");

        card.getElement().addEventListener("mouseover", e ->
                card.getStyle().set("box-shadow", "0 4px 12px rgba(0,0,0,0.10)"));
        card.getElement().addEventListener("mouseout", e ->
                card.getStyle().remove("box-shadow"));

        // ── Avatar row: colored circle + company name beside it ───────────────
        String color = avatarColor(company.getCompanyName());
        String initial = company.getCompanyName().substring(0, 1).toUpperCase();

        Div avatar = new Div();
        avatar.setText(initial);
        avatar.getStyle()
                .set("width", "52px").set("height", "52px")
                .set("min-width", "52px")
                .set("border-radius", "50%")
                .set("background", color + "22")   // light tint of the color
                .set("color", color)
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("font-size", "1.4rem")
                .set("font-weight", "600")
                .set("border", "2px solid " + color + "55");

        Span name = new Span(company.getCompanyName());
        name.getStyle()
                .set("font-weight", "500")
                .set("font-size", "1rem")
                .set("line-height", "1.3");

        HorizontalLayout avatarRow = new HorizontalLayout(avatar, name);
        avatarRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        avatarRow.setSpacing(true);
        avatarRow.getStyle().set("margin-bottom", "0.75rem");

        // ── Active badge ──────────────────────────────────────────────────────
        Span badge = new Span(company.isActive() ? "● Active" : "● Inactive");
        badge.getStyle()
                .set("font-size", "0.75rem")
                .set("padding", "3px 10px")
                .set("border-radius", "1rem")
                .set("background", company.isActive()
                        ? "var(--lumo-success-color-10pct)"
                        : "var(--lumo-error-color-10pct)")
                .set("color", company.isActive()
                        ? "var(--lumo-success-text-color)"
                        : "var(--lumo-error-text-color)");

        card.add(avatarRow, badge);
        card.addClickListener(e -> {
            // Cache the name so CompanyView can show it even when guest token
            // can't call getProductionCompany (backend doesn't support guest access)
            VaadinSession.getCurrent().setAttribute(
                    "company_name_" + company.getCompanyId(), company.getCompanyName());
            getUI().ifPresent(ui -> ui.navigate("company/" + company.getCompanyId()));
        });

        return card;
    }

    /** Picks a consistent color for a company based on its first letter. */
    private String avatarColor(String companyName) {
        int index = Character.toLowerCase(companyName.charAt(0)) % AVATAR_COLORS.length;
        return AVATAR_COLORS[Math.abs(index)];
    }
}
