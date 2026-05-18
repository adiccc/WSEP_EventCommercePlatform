package UI.Views;

import UI.Presenters.EventPresenter;
import application.EventService;
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
@Route(value = "event/:companyId(\\d+)/:eventId(\\d+)", layout = MainLayout.class)
@PageTitle("Event — EventCommerce")
@AnonymousAllowed
public class EventView extends VerticalLayout implements BeforeEnterObserver {

    private final EventPresenter presenter;

    private int companyId;
    private int eventId;

    public EventView(EventService eventService) {
        this.presenter = new EventPresenter(eventService);
        setPadding(true);
        setSpacing(true);
    }

    // ── Route parameters ──────────────────────────────────────────────────────

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        companyId = Integer.parseInt(
                event.getRouteParameters().get("companyId").orElse("0"));
        eventId = Integer.parseInt(
                event.getRouteParameters().get("eventId").orElse("0"));
        buildPage();
    }

    // ── Page layout ───────────────────────────────────────────────────────────

    private void buildPage() {
        removeAll();

        String token = (String) VaadinSession.getCurrent().getAttribute("token");

        // Back button → company page
        Button back = new Button("← Back to Company",
                e -> getUI().ifPresent(ui -> ui.navigate("company/" + companyId)));
        back.getElement().setAttribute("theme", "tertiary");

        // Load event
        var response = presenter.getEvent(token, companyId, eventId);
        if (response.getValue() == null) {
            add(back, new Paragraph("Error loading event: " + response.getMessage()));
            return;
        }

        EventDetailsDTO event = response.getValue();
        add(back, buildEventHeader(event), buildInfoSection(event), buildPolicySection(event));
    }

    // ── Event header ──────────────────────────────────────────────────────────

    private VerticalLayout buildEventHeader(EventDetailsDTO event) {
        VerticalLayout header = new VerticalLayout();
        header.setPadding(false);
        header.setSpacing(false);
        header.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "1.25rem")
                .set("margin-bottom", "0.5rem");

        H2 name = new H2(event.getName());
        name.getStyle().set("margin", "0 0 0.75rem 0");

        HorizontalLayout chips = new HorizontalLayout();
        chips.setSpacing(true);
        chips.add(infoChip("📅 " + event.getDate()));
        chips.add(infoChip("🗂 " + event.getCategoryEvent()));
        chips.add(infoChip("📍 " + event.getLocation()));

        header.add(name, chips);
        return header;
    }

    // ── Info section ──────────────────────────────────────────────────────────

    private VerticalLayout buildInfoSection(EventDetailsDTO event) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(false);
        section.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("margin-bottom", "0.5rem");

        H3 title = new H3("Event Info");
        title.getStyle().set("margin-top", "0");

        section.add(title);
        section.add(infoRow("Sale starts", event.getSaleStartDate()));

        return section;
    }

    // ── Policy section ────────────────────────────────────────────────────────

    private VerticalLayout buildPolicySection(EventDetailsDTO event) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(false);
        section.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");

        H3 title = new H3("Policies");
        title.getStyle().set("margin-top", "0");

        section.add(title);
        section.add(infoRow("Purchase policy", event.getPurchasePolicy()));
        section.add(infoRow("Discount policy", event.getDiscountPolicy()));

        return section;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Span infoChip(String text) {
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
