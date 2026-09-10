package ar.com.padelnec.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** Identidad y sellos de auditoria comunes a todas las entidades. */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    /**
     * El id lo genera Hibernate en la aplicacion (no el DEFAULT de la columna:
     * ese solo corre si un INSERT a mano no lo completa). Version 7 en vez del
     * aleatorio de siempre: mismo tamano y mismas garantias de unicidad, pero
     * ordenado en el tiempo, asi el indice no se fragmenta al insertar como si
     * cada fila cayera en una posicion al azar. Postgres 18 tiene su propio
     * uuidv7() para lo poco que se inserta por SQL a mano, pero ese DEFAULT no
     * se toca aca: el Postgres embebido de dev/test es version 14 y esa
     * funcion no existe ahi.
     */
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Igualdad por identificador. Dos instancias sin persistir nunca son iguales,
     * lo que evita que colapsen dentro de un Set antes de recibir su UUID.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        return id != null && id.equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(getClass().getSimpleName());
    }
}
