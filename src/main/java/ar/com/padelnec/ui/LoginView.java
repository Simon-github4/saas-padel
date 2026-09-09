package ar.com.padelnec.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

/** Entrada al panel del club. */
@Route("login")
@PageTitle("Ingresar | Panel del club")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public LoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setSpacing(false);
        addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.Gap.MEDIUM);

        login.setAction("login");
        login.setI18n(spanish());

        // La misma marca que encabeza el menu del panel: el que llega aca tiene
        // que reconocer que es el mismo producto antes de escribir su clave.
        Span mark = new Span("▦");
        mark.setClassName("brand-mark");
        mark.getElement().setAttribute("aria-hidden", "true");

        H1 title = new H1("Panel del club");
        title.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.Margin.NONE,
                LumoUtility.FontWeight.SEMIBOLD);

        VerticalLayout brand = new VerticalLayout(mark, title);
        brand.setPadding(false);
        brand.setSpacing(false);
        brand.setWidth(null);
        brand.setAlignItems(Alignment.CENTER);
        brand.addClassNames(LumoUtility.Gap.SMALL);

        Paragraph help = new Paragraph("Si no recordás tu clave, escribinos y te la restablecemos.");
        help.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY,
                LumoUtility.Margin.NONE, LumoUtility.TextAlignment.CENTER);

        add(brand, login, help);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Spring Security vuelve al login con ?error cuando las credenciales fallan.
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            login.setError(true);
        }
    }

    private LoginI18n spanish() {
        LoginI18n i18n = LoginI18n.createDefault();

        LoginI18n.Form form = i18n.getForm();
        form.setTitle("Ingresar");
        form.setUsername("Correo");
        form.setPassword("Clave");
        form.setSubmit("Entrar");
        form.setForgotPassword("Olvidé mi clave");

        LoginI18n.ErrorMessage error = i18n.getErrorMessage();
        error.setTitle("No pudimos ingresar");
        // Mismo mensaje para usuario inexistente y clave equivocada: distinguirlos le
        // confirmaria a un atacante que correos existen.
        error.setMessage("Revisá el correo y la clave.");

        i18n.setForm(form);
        i18n.setErrorMessage(error);
        return i18n;
    }
}
