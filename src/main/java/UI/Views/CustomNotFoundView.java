package UI.Views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.component.Html;
import jakarta.servlet.http.HttpServletResponse;

@PageTitle("404 - Page Not Found")
@AnonymousAllowed
public class CustomNotFoundView extends VerticalLayout
        implements HasErrorParameter<NotFoundException> {

    public CustomNotFoundView() {

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        getStyle()
                .set("position", "relative")
                .set("overflow", "hidden")
                .set("text-align", "center")
                .set("background-image",
                        "linear-gradient(to right, rgba(255,255,255,0.08), rgba(255,255,255,0.72), rgba(255,255,255,0.90)), "
                                + "url('images/background.png')")
                .set("background-size", "cover")
                .set("background-position", "center center")
                .set("background-repeat", "no-repeat");

        H1 title = new H1("404");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "8rem")
                .set("font-weight", "900")
                .set("letter-spacing", "10px")
                .set("line-height", "1")
                .set("color", "#111827")
                .set("text-shadow", "0 6px 18px rgba(255,255,255,0.85)");

        Image image = new Image("images/404.jpeg", "404 page");
        image.setMaxWidth("390px");

        image.getStyle()
                .set("width", "100%")
                .set("margin-top", "0.75rem")
                .set("border-radius", "22px")
                .set("box-shadow", "0 20px 45px rgba(15,23,42,0.32)");

        Html subtitle = new Html(
                "<p>We searched everywhere and could not find the page you were looking for.<br>"
                        + "We promise there is nothing here.</p>"
        );

        subtitle.getStyle()
                .set("font-size", "1.12rem")
                .set("color", "#1f2937")
                .set("font-weight", "600")
                .set("max-width", "650px")
                .set("margin-top", "1.5rem")
                .set("margin-bottom", "1.2rem")
                .set("line-height", "1.7")
                .set("text-shadow", "0 2px 10px rgba(255,255,255,0.85)")
                .set("text-align", "center");;

        Button homeButton = new Button(
                "Back to Home",
                e -> UI.getCurrent().navigate("")
        );

        homeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        homeButton.getStyle()
                .set("padding", "0.85rem 1.8rem")
                .set("font-size", "1rem")
                .set("font-weight", "700")
                .set("border-radius", "999px")
                .set("box-shadow", "0 10px 24px rgba(37,99,235,0.35)");

        add(title, image, subtitle, homeButton);
    }

    @Override
    public int setErrorParameter(
            BeforeEnterEvent event,
            ErrorParameter<NotFoundException> parameter) {

        return HttpServletResponse.SC_NOT_FOUND;
    }
}