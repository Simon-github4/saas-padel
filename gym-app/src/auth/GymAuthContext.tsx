import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react';
import { gymApi, type SessionResponse } from '../api/gymClient';

/**
 * Sesion del socio. Vive en localStorage bajo una clave por club, asi que un
 * mismo celular puede tener la de dos gimnasios sin pisarlas.
 *
 * <p>Si el navegador no deja usar localStorage (modo privado, datos bloqueados),
 * la app sigue andando: la sesion dura hasta cerrar la pestana.
 */
export interface GymSession {
  token: string;
  expiresAt: string;
  fullName: string;
  mustChangePassword: boolean;
}

interface AuthValue {
  slug: string;
  session: GymSession | null;
  login: (dni: string, password?: string) => Promise<void>;
  /**
   * La clave temporal que el socio acaba de escribir para entrar, si la app no se
   * recargo desde entonces. Sirve para no pedirle la misma clave dos veces.
   */
  temporaryPassword: string | null;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  logout: () => Promise<void>;
  /** El servidor dijo que la sesion ya no vale: se descarta sin avisarle de nuevo. */
  expire: () => void;
  /** El servidor exige cambiar la clave (p. ej. el mostrador la reseteo). */
  requirePasswordChange: () => void;
}

const AuthContext = createContext<AuthValue | null>(null);

const storageKey = (slug: string) => `gym_session_${slug}`;

function readStored(slug: string): GymSession | null {
  try {
    const raw = window.localStorage.getItem(storageKey(slug));
    if (!raw) {
      return null;
    }
    // Sin mirar cuando vence: la sesion es deslizante (el servidor la renueva con cada uso), asi que el
    // vencimiento que se guardo al entrar queda viejo. Si ya no vale, el servidor responde 401 y la
    // app vuelve al login.
    return JSON.parse(raw) as GymSession;
  } catch {
    return null;
  }
}

function writeStored(slug: string, session: GymSession | null) {
  try {
    if (session) {
      window.localStorage.setItem(storageKey(slug), JSON.stringify(session));
    } else {
      window.localStorage.removeItem(storageKey(slug));
    }
  } catch {
    // Sin almacenamiento: la sesion queda solo en memoria.
  }
}

function toSession(response: SessionResponse): GymSession {
  return {
    token: response.token,
    expiresAt: response.expiresAt,
    fullName: response.fullName,
    mustChangePassword: response.mustChangePassword,
  };
}

export function GymAuthProvider({ slug, children }: { slug: string; children: ReactNode }) {
  const [session, setSession] = useState<GymSession | null>(() => readStored(slug));
  // En memoria y nunca en localStorage: es la clave de otra persona (la del mostrador).
  const temporaryPasswordRef = useRef<string | null>(null);

  const store = useCallback(
    (next: GymSession | null) => {
      setSession(next);
      writeStored(slug, next);
    },
    [slug],
  );

  const login = useCallback(
    async (dni: string, password?: string) => {
      const response = await gymApi.login(slug, dni, password);
      temporaryPasswordRef.current = response.mustChangePassword && password ? password : null;
      store(toSession(response));
    },
    [slug, store],
  );

  const changePassword = useCallback(
    async (currentPassword: string, newPassword: string) => {
      if (!session) {
        return;
      }
      // El servidor cierra todas las sesiones y devuelve una nueva: la app sigue adentro.
      const response = await gymApi.changePassword(slug, session.token, currentPassword, newPassword);
      temporaryPasswordRef.current = null;
      store(toSession(response));
    },
    [slug, session, store],
  );

  const logout = useCallback(async () => {
    const current = session;
    temporaryPasswordRef.current = null;
    store(null);
    if (current) {
      // Si no hay red, la sesion ya se descarto en este celular: cerrar en el
      // servidor es un extra.
      await gymApi.logout(slug, current.token).catch(() => undefined);
    }
  }, [slug, session, store]);

  const expire = useCallback(() => {
    temporaryPasswordRef.current = null;
    store(null);
  }, [store]);

  const requirePasswordChange = useCallback(() => {
    if (session && !session.mustChangePassword) {
      store({ ...session, mustChangePassword: true });
    }
  }, [session, store]);

  const value = useMemo<AuthValue>(
    () => ({
      slug,
      session,
      login,
      temporaryPassword: temporaryPasswordRef.current,
      changePassword,
      logout,
      expire,
      requirePasswordChange,
    }),
    [slug, session, login, changePassword, logout, expire, requirePasswordChange],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useGymAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useGymAuth se usa dentro de GymAuthProvider');
  }
  return value;
}
