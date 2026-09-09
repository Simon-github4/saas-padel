package ar.com.padelnec.support;

/**
 * Confirma que los bytes subidos son de verdad del tipo que dice el
 * navegador, mirando la firma del archivo en vez de confiar en el
 * Content-Type declarado.
 *
 * <p>El Content-Type que manda el navegador al subir un archivo es el unico
 * dato que hay del tipo, pero no es de fiar: sin este chequeo, cualquier byte
 * etiquetado "image/png" quedaba guardado tal cual y se servia despues,
 * publico, bajo ese mismo content-type.
 */
public final class ImageSignature {

    public static boolean matches(byte[] data, String declaredContentType) {
        return switch (declaredContentType) {
            case "image/jpeg" -> matchesAt(data, 0, 0xFF, 0xD8, 0xFF);
            case "image/png" -> matchesAt(data, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/gif" -> matchesAt(data, 0, 'G', 'I', 'F', '8', '7', 'a')
                    || matchesAt(data, 0, 'G', 'I', 'F', '8', '9', 'a');
            case "image/webp" -> matchesAt(data, 0, 'R', 'I', 'F', 'F') && matchesAt(data, 8, 'W', 'E', 'B', 'P');
            default -> false;
        };
    }

    private static boolean matchesAt(byte[] data, int offset, int... signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xFF) != (signature[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    private ImageSignature() {
    }
}
