import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { playerApi, type PlayerSession } from '../api/client';

/**
 * Sesion del jugador, el primer estado global de la app.
 *
 * <p>No reemplaza el flujo de siempre: reservar y gestionar por
 * {@code /manage/:token} sigue andando igual para quien no inicia sesion. Esto es
 * un botón nuevo, no una puerta delante de lo que ya hay.
 */

const STORAGE_KEY = 'padel_player_session';

export interface AuthState {
  token: string;
  accountId: string;
  email: string;
  emailVerified: boolean;
  phoneNumber: string | null;
  displayName: string | null;
}

interface AuthContextValue {
  session: AuthState | null;
  /** Todavía no crea la cuenta: manda el código/link. Ver {@link confirmSignup}. */
  register: (email: string, password: string, displayName?: string, phoneNumber?: string) => Promise<void>;
  /** Confirma el alta con el código de 6 dígitos: recién acá se crea la cuenta y se abre sesión. */
  confirmSignup: (email: string, code: string) => Promise<AuthState>;
  login: (email: string, password: string) => Promise<AuthState>;
  loginWithGoogle: (idToken: string) => Promise<AuthState>;
  logout: () => void;
  /** Actualiza nombre (y teléfono, si corresponde) en la sesión sin volver a autenticar. */
  updateLocalProfile: (name: string, phoneNumber?: string) => void;
  /** El backend respondio SESSION_EXPIRED: se cae la sesion sin volver a llamar a nadie. */
  clearExpiredSession: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function toAuthState(session: PlayerSession): AuthState {
  return {
    token: session.token,
    accountId: session.accountId,
    email: session.email,
    emailVerified: session.emailVerified,
    phoneNumber: session.phoneNumber,
    displayName: session.displayName,
  };
}

function readStoredSession(): AuthState | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<AuthState>;
    return parsed.token && parsed.email
      ? {
          token: parsed.token,
          accountId: parsed.accountId ?? '',
          email: parsed.email,
          emailVerified: parsed.emailVerified ?? false,
          phoneNumber: parsed.phoneNumber ?? null,
          displayName: parsed.displayName ?? null,
        }
      : null;
  } catch {
    return null;
  }
}

function storeSession(session: AuthState | null) {
  try {
    if (session) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Sin localStorage (privado, cuota llena) la sesion sigue andando en memoria
    // para esta pestaña; solo no persiste entre visitas.
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthState | null>(() => readStoredSession());

  const register = useCallback(
    (email: string, password: string, displayName?: string, phoneNumber?: string) =>
      playerApi.register(email, password, displayName, phoneNumber),
    [],
  );

  const confirmSignup = useCallback(async (email: string, code: string) => {
    const result = await playerApi.confirmSignup(email, code);
    const next = toAuthState(result);
    storeSession(next);
    setSession(next);
    return next;
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const result = await playerApi.login(email, password);
    const next = toAuthState(result);
    storeSession(next);
    setSession(next);
    return next;
  }, []);

  const loginWithGoogle = useCallback(async (idToken: string) => {
    const result = await playerApi.loginWithGoogle(idToken);
    const next = toAuthState(result);
    storeSession(next);
    setSession(next);
    return next;
  }, []);

  // El backend solo completa el telefono si la cuenta todavia no tenia uno
  // (ver PlayerAuthService.updateProfile); acá se refleja la misma regla en
  // el estado local, para no pisar un telefono que el jugador ya tenía
  // guardado con uno distinto que haya tipeado en este checkout puntual.
  const updateLocalProfile = useCallback(
    (name: string, phoneNumber?: string) => {
      if (!session) {
        return;
      }
      const next = {
        ...session,
        displayName: name,
        phoneNumber: session.phoneNumber ?? phoneNumber ?? null,
      };
      storeSession(next);
      setSession(next);
    },
    [session],
  );

  const logout = useCallback(() => {
    const token = session?.token;
    storeSession(null);
    setSession(null);
    if (token) {
      // No bloquea el logout de la pantalla: si el pedido falla, la sesion
      // vence sola a los 90 dias y no puede volver a usarse desde este navegador.
      void playerApi.logout(token).catch(() => {});
    }
  }, [session]);

  const clearExpiredSession = useCallback(() => {
    storeSession(null);
    setSession(null);
  }, []);

  const value = useMemo(
    () => ({
      session,
      register,
      confirmSignup,
      login,
      loginWithGoogle,
      logout,
      updateLocalProfile,
      clearExpiredSession,
    }),
    [session, register, confirmSignup, login, loginWithGoogle, logout, updateLocalProfile, clearExpiredSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function usePlayerAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('usePlayerAuth se tiene que usar adentro de AuthProvider');
  }
  return context;
}
