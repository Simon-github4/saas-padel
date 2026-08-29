package ar.com.padelnec.config;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Club activo para el hilo en curso.
 *
 * <p>Lo lee {@link TenantIdentifierResolver} para que Hibernate agregue el filtro
 * por {@code club_id} a toda consulta. Se establece en el borde de cada entrada al
 * sistema: el filtro HTTP para la API publica, la sesion del usuario para el panel,
 * y club por club dentro de los jobs programados.
 *
 * <p>Si nadie lo establecio, el resolver devuelve {@link #UNSCOPED}: un club que no
 * existe. Las consultas no devuelven nada y los insert fallan contra la clave
 * foranea. El sistema falla cerrado, nunca abierto.
 */
public final class TenantContext {

    /** Sin contexto: no coincide con ningun club real. */
    public static final UUID UNSCOPED = new UUID(0L, 0L);

    /** Acceso de plataforma sin filtro. Solo para lectura desde codigo de soporte. */
    public static final UUID ROOT = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID clubId) {
        CURRENT.set(clubId);
    }

    public static UUID get() {
        UUID current = CURRENT.get();
        return current != null ? current : UNSCOPED;
    }

    /** El club actual, o falla si no hay ninguno. Para servicios que exigen contexto. */
    public static UUID require() {
        UUID current = CURRENT.get();
        if (current == null || UNSCOPED.equals(current) || ROOT.equals(current)) {
            throw new IllegalStateException(
                    "No hay un club en contexto para esta operacion");
        }
        return current;
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Ejecuta el trabajo con el club indicado y restaura el contexto anterior al
     * terminar. Es la forma correcta de recorrer clubes dentro de un job.
     */
    public static <T> T callAs(UUID clubId, Supplier<T> work) {
        UUID previous = CURRENT.get();
        CURRENT.set(clubId);
        try {
            return work.get();
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }

    public static void runAs(UUID clubId, Runnable work) {
        callAs(clubId, () -> {
            work.run();
            return null;
        });
    }
}
