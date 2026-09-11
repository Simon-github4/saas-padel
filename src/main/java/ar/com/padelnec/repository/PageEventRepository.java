package ar.com.padelnec.repository;

import ar.com.padelnec.domain.PageEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PageEventRepository extends JpaRepository<PageEvent, UUID> {

    /**
     * Que posiciones de esta visita ya estan guardadas.
     *
     * <p>Un lote puede llegar dos veces: {@code sendBeacon} reintenta, y el
     * navegador que se cierra a mitad de envio no se entera de si llego. Sin este
     * filtro previo, el lote repetido choca contra {@code ux_page_event_session_seq}
     * y se pierde entero, incluidos los eventos que si eran nuevos.
     */
    @Query("select e.seq from PageEvent e where e.sessionId = ?1 and e.seq in ?2")
    List<Integer> findStoredSeqs(UUID sessionId, Collection<Integer> seqs);

    /** Borrado por lote para el job de retencion: no trae las filas a memoria. */
    @Modifying
    @Query("delete from PageEvent e where e.createdAt < ?1")
    int deleteOlderThan(Instant cutoff);
}
