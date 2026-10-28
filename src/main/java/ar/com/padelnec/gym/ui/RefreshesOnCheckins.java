package ar.com.padelnec.gym.ui;

/**
 * Pantalla del gimnasio que se tiene que redibujar cuando un socio entra escaneando
 * el QR mientras esta abierta.
 *
 * <p>El que se entera es el marco del panel ({@code MainLayout}), en su poll: sin esto
 * el aviso sonaba arriba, pero la lista de abajo seguia sin el socio hasta recargar.
 */
public interface RefreshesOnCheckins {

    void checkinsArrived();
}
