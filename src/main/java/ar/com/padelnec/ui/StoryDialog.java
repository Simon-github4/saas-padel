package ar.com.padelnec.ui;

import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.TenantStoryTemplate;
import ar.com.padelnec.domain.enums.StoryStyle;
import ar.com.padelnec.repository.TenantStoryTemplateRepository;
import ar.com.padelnec.service.DayStoryService;
import ar.com.padelnec.service.DayStoryService.DayStory;
import ar.com.padelnec.service.DayStoryService.StoryTime;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.support.ImageSignature;
import ar.com.padelnec.support.StoryTemplateImages;
import ar.com.padelnec.web.PanelStoryController;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.dom.Element;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.databind.ObjectMapper;

/**
 * La historia de Instagram con los turnos libres del dia, lista para subir.
 *
 * <p>Los clubes publican cada dia una historia con su plantilla (foto de la cancha,
 * logo, "Turnos disponibles") y los horarios que les quedan escritos encima, y
 * hasta ahora la armaban a mano en Canva. Aca la plantilla se sube una vez, se
 * marca en que franja van los horarios, y la imagen sale de la agenda del dia.
 *
 * <p>La imagen se dibuja en el navegador, en un canvas de 1080 x 1920: la vista
 * previa se mueve en el acto mientras se corre la franja, usa la misma fuente del
 * panel sin depender de las que tenga instaladas el servidor, y "Compartir" puede
 * pasarle el archivo directo a Instagram desde el celular. Ese toque se atiende
 * en el navegador y no en el servidor, por lo mismo que en {@link DevicePushItem}:
 * compartir solo se permite en el momento del toque.
 *
 * <p>El dibujo vive en {@code resources/panel/historia.js} y lo sirve, con cache,
 * {@link PanelStoryController}, igual que la plantilla y el logo.
 */
class StoryDialog extends Dialog {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Lo que se puede elegir para subir. Llega achicada (la achica el navegador antes de
     * mandarla, ver historia.js), asi que una foto grande del celular no es problema.
     */
    private static final int MAX_TEMPLATE_BYTES = 15 * 1024 * 1024;
    private static final Set<String> TEMPLATE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    /** Lo minimo que puede medir la franja de los horarios, en porcentaje del alto. */
    private static final int MIN_AREA = 20;

    /** Naranja de fabrica, igual que en la app del jugador cuando el club no eligio color. */
    private static final String DEFAULT_COLOR = "#ea580c";

    /** Los dos botones copian el link de reservas: la imagen sola no lleva a ningun lado. */
    private static final String PHONE_HINT = "Al compartir se copia el link de reservas: pegalo en el "
            + "sticker «Enlace» de Instagram o en el texto del estado.";
    private static final String DESKTOP_HINT = "Desde el celular podés compartirla directo a tu historia o "
            + "estado. Al descargar se copia el link de reservas, para pegarlo en el sticker «Enlace» "
            + "o en el texto del estado.";

    private final UUID clubId;
    private final DayStoryService dayStoryService;
    private final TenantService tenantService;
    private final TenantStoryTemplateRepository templates;
    private final String publicUrl;
    private final String publicLink;

    private Tenant club;
    private DayStory story;
    /** De cuando es la plantilla del club, o null si no tiene. */
    private Instant templateVersion;

    private final Element canvas = new Element("canvas");
    private final DatePicker date = new DatePicker();
    private final Paragraph status = new Paragraph();
    private final CheckboxGroup<StoryTime> times = new CheckboxGroup<>();
    private final Select<StoryStyle> style = new Select<>();
    private final Checkbox title = new Checkbox("Escribir «Turnos disponibles» arriba de los horarios");
    private final Input areaTop = new Input();
    private final Input areaBottom = new Input();
    private final Div templateOnly = new Div();
    private final Button removeTemplate = new Button("Quitar plantilla");
    private final MemoryBuffer buffer = new MemoryBuffer();
    private final Upload upload = new Upload(buffer);
    private final Button share = new Button("Compartir", VaadinIcon.SHARE.create());
    private final Button download = new Button("Descargar", VaadinIcon.DOWNLOAD.create());
    private final Span shareHint = new Span();

    StoryDialog(LocalDate day, DayStoryService dayStoryService, TenantService tenantService,
                TenantStoryTemplateRepository templates, String publicLink) {
        // Recien leido y no el de la agenda: esa copia no se entera de lo que cambio
        // la ultima vez que se abrio este dialogo.
        this.club = tenantService.requireCurrent();
        this.clubId = club.getId();
        this.templateVersion = templates.findUpdatedAt(clubId).orElse(null);
        this.dayStoryService = dayStoryService;
        this.tenantService = tenantService;
        this.templates = templates;
        this.publicUrl = publicLink;
        this.publicLink = publicLink.replaceFirst("^https?://", "");

        setHeaderTitle("Historia de turnos libres");
        addClassName("story-dialog");
        setWidth("min(48rem, 100vw)");

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel("Cerrar");
        getHeader().add(close);

        canvas.getClassList().add("story-preview");
        canvas.setAttribute("aria-label", "Vista previa de la historia");
        Div preview = new Div();
        preview.addClassName("story-dialog__preview");
        preview.getElement().appendChild(canvas);

        VerticalLayout controls = new VerticalLayout(dayControls(), times, design());
        controls.setPadding(false);
        controls.addClassName("story-dialog__controls");

        Div body = new Div(preview, controls);
        body.addClassName("story-dialog__body");
        add(body);

        share.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        share.setVisible(false);
        download.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        shareHint.addClassName("story-dialog__share-hint");
        shareHint.setText(DESKTOP_HINT);
        getFooter().add(shareHint, download, share);

        refreshTemplate();
        date.setValue(day);

        addAttachListener(event -> {
            // Con cache: se baja una vez y no viaja con cada apertura (ver PanelStoryController).
            event.getUI().getPage().addJavaScript(PanelStoryController.SCRIPT_URL);
            upload.getElement().executeJs("window.padelStory.bindUpload(this)");
            // Los toques de Compartir y Descargar no pasan por el servidor (ver la clase).
            share.getElement().executeJs("this.addEventListener('click', () => window.padelStory.share($0))",
                    canvas);
            download.getElement().executeJs(
                    "this.addEventListener('click', () => window.padelStory.download($0))", canvas);
            areaTop.getElement().executeJs("window.padelStory.bindRange(this, $0, 'top')", canvas);
            areaBottom.getElement().executeJs("window.padelStory.bindRange(this, $0, 'bottom')", canvas);
            // En la compu Compartir abre el menu del sistema, que no lleva a Instagram:
            // solo se ofrece en el celular, y ahi pasa a ser el boton principal.
            event.getUI().getPage().executeJs("return window.padelStory.canShare()")
                    .then(Boolean.class, canShare -> {
                        share.setVisible(Boolean.TRUE.equals(canShare));
                        if (Boolean.TRUE.equals(canShare)) {
                            shareHint.setText(PHONE_HINT);
                            download.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
                        }
                    });
            render();
        });
    }

    // ------------------------------------------------------------------ dia

    private VerticalLayout dayControls() {
        date.setLabel("Día");
        date.setI18n(SpanishDates.datePicker());
        date.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                loadDay(event.getValue());
            }
        });
        status.addClassName("story-dialog__status");

        times.setLabel("Horarios a publicar");
        times.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        times.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                render();
            }
        });

        VerticalLayout day = new VerticalLayout(date, status);
        day.setPadding(false);
        day.setSpacing(false);
        return day;
    }

    private void loadDay(LocalDate day) {
        story = dayStoryService.storyFor(club, day);
        boolean manyCourts = story.times().stream().anyMatch(t -> t.freeCourts() > 1);
        times.setItemLabelGenerator(t -> manyCourts
                ? t.label() + " · " + t.freeCourts() + (t.freeCourts() == 1 ? " cancha" : " canchas")
                : t.label());
        times.setItems(story.times());
        times.setValue(Set.copyOf(story.times()));
        times.setVisible(!story.times().isEmpty());
        status.setText(statusText(day));
        render();
    }

    private String statusText(LocalDate day) {
        if (!story.bookable()) {
            return day.isBefore(LocalDate.now(club.zoneId()))
                    ? "Ese día ya pasó."
                    : "Ese día todavía no está abierto a reservas: la web muestra hasta "
                            + club.getBookingHorizonDays() + " días adelante.";
        }
        int count = story.times().size();
        if (count == 0) {
            return "No quedan horarios libres ese día.";
        }
        return count == 1
                ? "Queda 1 horario libre. Destildá el que no quieras publicar."
                : "Quedan " + count + " horarios libres. Destildá los que no quieras publicar.";
    }

    // --------------------------------------------------------------- diseño

    private Details design() {
        upload.setAcceptedFileTypes(TEMPLATE_TYPES.toArray(new String[0]));
        upload.setMaxFiles(1);
        upload.setMaxFileSize(MAX_TEMPLATE_BYTES);
        upload.setUploadButton(new Button("Subir plantilla", VaadinIcon.UPLOAD.create()));
        upload.setDropLabel(new Span("o arrastrala acá"));
        upload.addSucceededListener(event -> {
            upload.clearFileList();
            try {
                byte[] data = buffer.getInputStream().readAllBytes();
                // El tipo sale del contenido y no del que declara el navegador: el panel
                // manda la plantilla ya pasada a JPG, pero declarada con el tipo del
                // archivo original que se eligio.
                Optional<String> type = TEMPLATE_TYPES.stream()
                        .filter(candidate -> ImageSignature.matches(data, candidate))
                        .findFirst();
                if (type.isEmpty()) {
                    error("Formato no soportado: usá una imagen JPG, PNG o WEBP");
                    return;
                }
                StoryTemplateImages.Image shrunk = StoryTemplateImages.shrink(data, type.get());
                TenantStoryTemplate template = templates.findById(clubId).orElseGet(TenantStoryTemplate::new);
                template.setTenantId(clubId);
                template.setData(shrunk.data());
                template.setContentType(shrunk.contentType());
                template.setUpdatedAt(Instant.now());
                templateVersion = templates.save(template).getUpdatedAt();
                refreshTemplate();
                render();
                Notification.show("Plantilla guardada. Ahora mové la franja hasta la zona libre.");
            } catch (IOException e) {
                error("No se pudo subir la plantilla");
            }
        });
        upload.addFileRejectedListener(event -> error(event.getErrorMessage()));

        removeTemplate.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        removeTemplate.addClickListener(event -> {
            templates.deleteById(clubId);
            templateVersion = null;
            refreshTemplate();
            render();
        });

        Paragraph help = new Paragraph("La imagen vertical que usás para tus historias (1080 × 1920), "
                + "sin los horarios. Sin plantilla se usa un fondo con el color del club.");
        help.addClassName("story-dialog__help");

        style.setLabel("Estilo de los horarios");
        style.setItems(StoryStyle.values());
        style.setItemLabelGenerator(StoryDialog::styleLabel);
        style.setValue(club.getStoryStyle());
        style.addValueChangeListener(event -> {
            if (event.isFromClient() && event.getValue() != null) {
                save(c -> c.setStoryStyle(event.getValue()));
            }
        });

        title.setValue(club.isStoryTitle());
        title.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                save(c -> c.setStoryTitle(event.getValue()));
            }
        });

        templateOnly.add(range(areaTop, "Los horarios empiezan en", club.getStoryAreaTop(), true),
                range(areaBottom, "y terminan en", club.getStoryAreaBottom(), false), title);
        templateOnly.addClassName("story-dialog__template-only");

        VerticalLayout content = new VerticalLayout(help, upload, removeTemplate, style, templateOnly);
        content.setPadding(false);
        content.setSpacing(false);
        content.addClassName("story-dialog__design");

        Details details = new Details("Plantilla y diseño", content);
        details.setOpened(templateVersion == null);
        return details;
    }

    /** Un deslizador nativo: el de Vaadin no avisa mientras se arrastra, y la vista previa si. */
    private Div range(Input input, String label, int value, boolean top) {
        input.setType("range");
        input.getElement().setAttribute("min", "0");
        input.getElement().setAttribute("max", "100");
        input.setValue(String.valueOf(value));
        input.setId(top ? "story-area-top" : "story-area-bottom");
        input.addValueChangeListener(event -> saveArea(top, event.getValue()));

        NativeLabel caption = new NativeLabel(label);
        caption.setFor(input);
        Div field = new Div(caption, input);
        field.addClassName("story-dialog__range");
        return field;
    }

    /**
     * Las dos puntas se corrigen entre si para que la franja nunca quede mas chica
     * que {@link #MIN_AREA}: subir el comienzo por encima del final empuja el final.
     */
    private void saveArea(boolean top, String raw) {
        int value;
        try {
            value = Math.max(0, Math.min(100, Integer.parseInt(raw)));
        } catch (NumberFormatException e) {
            return;
        }
        int start = top ? Math.min(value, 100 - MIN_AREA) : club.getStoryAreaTop();
        int end = top ? club.getStoryAreaBottom() : Math.max(value, MIN_AREA);
        if (end - start < MIN_AREA) {
            if (top) {
                end = start + MIN_AREA;
            } else {
                start = end - MIN_AREA;
            }
        }
        int newTop = start;
        int newBottom = end;
        save(c -> {
            c.setStoryAreaTop(newTop);
            c.setStoryAreaBottom(newBottom);
        });
        areaTop.setValue(String.valueOf(club.getStoryAreaTop()));
        areaBottom.setValue(String.valueOf(club.getStoryAreaBottom()));
    }

    private void refreshTemplate() {
        if (templateVersion != null) {
            canvas.setAttribute("data-template", PanelStoryController.templateUrl(templateVersion.toEpochMilli()));
        } else {
            canvas.removeAttribute("data-template");
        }
        removeTemplate.setVisible(templateVersion != null);
        templateOnly.setVisible(templateVersion != null);
    }

    /** Los cambios de diseño se guardan al toque: no hay un "Guardar" que olvidarse de tocar. */
    private void save(Consumer<Tenant> changes) {
        try {
            club = tenantService.update(club.getId(), changes);
        } catch (ObjectOptimisticLockingFailureException ex) {
            club = tenantService.update(club.getId(), changes);
        }
        render();
    }

    // -------------------------------------------------------------- dibujo

    private void render() {
        if (story == null || !isAttached()) {
            return;
        }
        Set<StoryTime> chosen = times.getValue();
        List<String> labels = story.times().stream().filter(chosen::contains).map(StoryTime::label).toList();
        String color = club.getPrimaryColor() == null ? DEFAULT_COLOR : club.getPrimaryColor();
        String accent = club.getSecondaryColor() == null ? color : club.getSecondaryColor();

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("template", templateVersion != null);
        spec.put("day", story.dayLabel());
        spec.put("times", labels);
        spec.put("empty", "Sin turnos libres");
        spec.put("top", club.getStoryAreaTop());
        spec.put("bottom", club.getStoryAreaBottom());
        spec.put("style", club.getStoryStyle().name());
        spec.put("title", club.isStoryTitle());
        spec.put("color", color);
        spec.put("accent", accent);
        spec.put("club", club.getName());
        spec.put("link", publicLink);
        spec.put("url", publicUrl);
        spec.put("logo", PanelStoryController.LOGO_URL);
        spec.put("file", "turnos-" + story.date() + ".jpg");
        canvas.executeJs("window.padelStory.render(this, JSON.parse($0))", JSON.writeValueAsString(spec));
    }

    private static String styleLabel(StoryStyle style) {
        return switch (style) {
            case PILLS -> "Recuadros blancos";
            case COLOR -> "Recuadros del color del club";
            case LIGHT -> "Texto blanco";
            case DARK -> "Texto negro";
        };
    }

    private static void error(String message) {
        Notification.show(message).addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
