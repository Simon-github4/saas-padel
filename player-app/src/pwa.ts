/// <reference types="vite/client" />
import { useSyncExternalStore } from 'react';

/**
 * La app del jugador como app instalable (PWA): el service worker y el pedido de
 * instalación que ofrece Chrome en Android.
 *
 * <p>El pedido de instalación ({@code beforeinstallprompt}) llega una sola vez, al
 * cargar la página y no cuando el jugador entra a /instalar: por eso se escucha
 * acá, apenas arranca la app, y se guarda hasta que alguien toque "Instalar".
 */

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
};

/** Dónde se está abriendo la página, para saber qué pasos mostrarle. */
export type InstallPlatform = 'android' | 'ios' | 'desktop' | 'installed';

export interface InstallState {
  platform: InstallPlatform;
  /** Chrome ya ofrece instalar: alcanza con un botón, sin pasos a mano. */
  canPrompt: boolean;
  /**
   * El link se abrió adentro de otra app (Instagram, Facebook o la vista web de
   * Android). Desde ahí no se puede instalar: primero hay que pasar al navegador.
   */
  inAppBrowser: boolean;
  /** En iPhone, un navegador que no es Safari: el menú para instalar es otro, o no está. */
  iosNotSafari: boolean;
}

let installEvent: BeforeInstallPromptEvent | null = null;
let installed = false;
let state: InstallState = snapshot();
const listeners = new Set<() => void>();

function emit() {
  state = snapshot();
  listeners.forEach((listener) => listener());
}

function isStandalone() {
  try {
    return window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
  } catch {
    return false;
  }
}

function snapshot(): InstallState {
  const ua = typeof navigator === 'undefined' ? '' : navigator.userAgent;
  // El iPad con iPadOS se presenta como una Mac; lo delata la pantalla táctil.
  const ios = /iPad|iPhone|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
  const android = /Android/.test(ua);
  const inAppBrowser = /Instagram|FBAN|FBAV|FB_IAB|Line\/|; wv\)/.test(ua);
  let platform: InstallPlatform = ios ? 'ios' : android ? 'android' : 'desktop';
  if (installed || (typeof window !== 'undefined' && isStandalone())) {
    platform = 'installed';
  }
  return {
    platform,
    canPrompt: installEvent !== null,
    inAppBrowser,
    iosNotSafari: ios && /CriOS|FxiOS|EdgiOS|OPiOS/.test(ua),
  };
}

/**
 * Registra el service worker y empieza a escuchar el pedido de instalación.
 *
 * <p>Solo en el build: en desarrollo, un service worker registrado sigue vivo
 * entre recargas y confunde más de lo que ayuda.
 */
export function setupPwa() {
  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    installEvent = event as BeforeInstallPromptEvent;
    emit();
  });
  window.addEventListener('appinstalled', () => {
    installed = true;
    installEvent = null;
    emit();
  });

  if (import.meta.env.PROD && 'serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch(() => {
        // Sin service worker la app anda igual; solo no muestra el aviso de sin conexión.
      });
    });
  }
}

/** Abre el cartel de instalación de Chrome. Devuelve si el jugador aceptó. */
export async function promptInstall(): Promise<boolean> {
  const event = installEvent;
  if (!event) {
    return false;
  }
  await event.prompt();
  const choice = await event.userChoice;
  // El evento sirve una sola vez, acepte o no.
  installEvent = null;
  emit();
  return choice.outcome === 'accepted';
}

export function useInstallState(): InstallState {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => state,
  );
}

declare global {
  interface Navigator {
    /** Safari en iPhone: true cuando la página se abrió desde el ícono del inicio. */
    standalone?: boolean;
  }
}
