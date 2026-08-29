package ar.com.padelnec.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

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

        login.setAction("login");
        login.setI18n(spanish());

        add(new H1("Panel del club"), login,
                new Paragraph("Si no recordas tu clave, escribinos y te la restablecemos."));
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
        form.setForgotPassword("Olvide mi clave");

        LoginI18n.ErrorMessage error = i18n.getErrorMessage();
        error.setTitle("No pudimos ingresar");
        // Mismo mensaje para usuario inexistente y clave equivocada: distinguirlos le
        // confirmaria a un atacante que correos existen.
        error.setMessage("Revisa el correo y la clave.");

        i18n.setForm(form);
        i18n.setErrorMessage(error);
        return i18n;
    }
}
