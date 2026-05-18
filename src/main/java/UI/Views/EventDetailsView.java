package UI.Views;

import UI.Presenters.EventDetailsPresenter;
import application.EventService;
import application.Response;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import domain.dto.EventDetailsDTO;

/**
 * Event details page.
 *
 * URL pattern: /event/42/7  (42 = companyId, 7 = eventId)
 */
@Route(value = "event/:companyId/:eventId", layout = MainLayout.class)
@PageTitle("Event Details — EventCommerce")
@AnonymousAllowed
public class EventDetailsView extends VerticalLayout implements BeforeEnterObserver {

    private final EventDetailsPresenter presenter;

    private int companyId;

    public EventDetailsView(EventService eventService) {
        this.presenter = new EventDetailsPresenter(eventService);
        setSpacing(true);
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = Integer.parseInt(
                event.getRouteParameters().get("companyId").orElse("0"));
        int eventId = Integer.parseInt(
                event.getRouteParameters().get("eventId").orElse("0"));
        loadEvent(companyId, eventId);
    }

    private void loadEvent(int companyId, int eventId) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");

        Button back = new Button("← Back to Company",
                e -> getUI().ifPresent(ui -> ui.navigate("company/" + companyId)));
        back.getElement().setAttribute("theme", "tertiary");

        Response<EventDetailsDTO> response = presenter.getDetails(token, companyId, eventId);

        removeAll();

        if (response.getValue() == null) {
            add(back, new Paragraph("Error loading event: " + response.getMessage()));
            return;
        }

        EventDetailsDTO dto = response.getValue();
        add(back, buildHeader(dto), buildInfoSection(dto), buildPolicySection(dto));
    }

    // ── Event header ──────────────────────────────────────────────────────────

    private VerticalLayout buildHeader(EventDetailsDTO dto) {
        VerticalLayout header = new VerticalLayout();
        header.setPadding(false);
        header.setSpacing(false);
        header.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "1.25rem")
                .set("margin-bottom", "0.5rem");

        H2 name = new H2(dto.getName());
        name.getStyle().set("margin", "0 0 0.75rem 0");

        HorizontalLayout chips = new HorizontalLayout();
        chips.setSpacing(true);
        chips.add(chip("📅 " + dto.getDate()));
        chips.add(chip("🗂 " + dto.getCategoryEvent()));
        chips.add(chip("📍 " + dto.getLocation()));

        header.add(name, chips);
        return header;
    }

    // ── Info section ──────────────────────────────────────────────────────────

    private VerticalLayout buildInfoSection(EventDetailsDTO dto) {
        VerticalLayout section = section("Event Info");
        section.add(infoRow("Sale starts", dto.getSaleStartDate()));
        return section;
    }

    // ── Policy section ────────────────────────────────────────────────────────

    private VerticalLayout buildPolicySection(EventDetailsDTO dto) {
        VerticalLayout section = section("Policies");
        section.add(infoRow("Purchase policy", dto.getPurchasePolicy()));
        section.add(infoRow("Discount policy", dto.getDiscountPolicy()));
        return section;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VerticalLayout section(String title) {
        VerticalLayout s = new VerticalLayout();
        s.setPadding(true);
        s.setSpacing(false);
        s.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("margin-bottom", "0.5rem");
        H3 h = new H3(title);
        h.getStyle().set("margin-top", "0");
        s.add(h);
        return s;
    }

    private Span chip(String text) {
        Span chip = new Span(text);
        chip.getStyle()
                .set("font-size", "0.85rem")
                .set("padding", "3px 10px")
                .set("border-radius", "1rem")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-secondary-text-color)");
        return chip;
    }

    private HorizontalLayout infoRow(String label, String value) {
        Span labelSpan = new Span(label + ":");
        labelSpan.getStyle()
                .set("font-weight", "600")
                .set("min-width", "160px")
                .set("color", "var(--lumo-secondary-text-color)");
        Span valueSpan = new Span(value != null ? value : "—");
        HorizontalLayout row = new HorizontalLayout(labelSpan, valueSpan);
        row.setSpacing(true);
        row.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
        row.getStyle().set("padding", "0.25rem 0");
        return row;
    }
}
