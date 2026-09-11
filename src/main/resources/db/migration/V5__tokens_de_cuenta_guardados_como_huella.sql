-- =====================================================================
-- Los tokens de cuenta se guardan como huella, no en claro.
--
-- El token de sesion, el de reseteo de contrasena y el del link que
-- confirma un alta son credenciales: quien los tenga entra como ese
-- jugador, le cambia la contrasena o le crea la cuenta. Estaban
-- guardados tal cual, asi que una copia de la base -- un backup mal
-- guardado, un acceso de soporte, un dump pedido para depurar algo --
-- alcanzaba para quedarse con todas las sesiones abiertas.
--
-- Con la huella, esa copia ya no sirve para entrar: el valor que abre
-- la puerta solo existe en el navegador del jugador y en el mail que
-- recibio.
--
-- SHA-256 y no bcrypt, que es lo que se usa para las contrasenas: estos
-- tokens son 32 bytes de SecureRandom, no algo que una persona elige, y
-- contra 256 bits de azar no hay diccionario que sirva. Ademas la
-- busqueda es por igualdad, y una sal al azar obligaria a recorrer la
-- tabla entera comparando de a una fila.
--
-- Las filas existentes se convierten en el lugar en vez de borrarse:
-- sha256 es el mismo calculo que hace la aplicacion (TokenHash), asi
-- que las sesiones abiertas siguen abiertas y ningun jugador tiene que
-- volver a entrar por un cambio interno. El ancho no cambia: 64
-- caracteres hexadecimales entran justo donde entraba el token.
--
-- Las columnas se renombran a proposito. Dejarlas llamandose "token"
-- invita a que alguien lea ese valor manana y lo trate como si fuera el
-- token de verdad.
-- =====================================================================

-- --------------------------------------------------------- sesiones
UPDATE player_session SET token = encode(sha256(token::bytea), 'hex');
ALTER TABLE player_session RENAME COLUMN token TO token_hash;
ALTER TABLE player_session RENAME CONSTRAINT ux_player_session_token TO ux_player_session_token_hash;

-- ------------------------------------------- reseteo de contrasena
UPDATE player_account
   SET password_reset_token = encode(sha256(password_reset_token::bytea), 'hex')
 WHERE password_reset_token IS NOT NULL;
ALTER TABLE player_account RENAME COLUMN password_reset_token TO password_reset_token_hash;
ALTER TABLE player_account
    RENAME CONSTRAINT ux_player_account_password_reset_token TO ux_player_account_password_reset_token_hash;

-- ------------------------------------------------ confirmacion de alta
UPDATE player_signup_pending SET confirm_token = encode(sha256(confirm_token::bytea), 'hex');
ALTER TABLE player_signup_pending RENAME COLUMN confirm_token TO confirm_token_hash;
