import { useEffect, useMemo, useState } from 'react';

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
};

export type InstallPlatform = 'android' | 'ios' | 'desktop' | 'installed';

function isStandalone() {
  return window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
}

function platform(): InstallPlatform {
  if (isStandalone()) {
    return 'installed';
  }
  const ua = window.navigator.userAgent;
  if (/iPad|iPhone|iPod/.test(ua)) {
    return 'ios';
  }
  if (/Android/.test(ua)) {
    return 'android';
  }
  return 'desktop';
}

export function useInstallPrompt() {
  const [installEvent, setInstallEvent] = useState<BeforeInstallPromptEvent | null>(null);
  const [installed, setInstalled] = useState(isStandalone);

  useEffect(() => {
    const onBeforeInstallPrompt = (event: Event) => {
      event.preventDefault();
      setInstallEvent(event as BeforeInstallPromptEvent);
    };
    const onInstalled = () => {
      setInstalled(true);
      setInstallEvent(null);
    };

    window.addEventListener('beforeinstallprompt', onBeforeInstallPrompt);
    window.addEventListener('appinstalled', onInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', onBeforeInstallPrompt);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, []);

  return useMemo(
    () => ({
      canPrompt: installEvent !== null,
      installed,
      platform: installed ? 'installed' : platform(),
      async prompt() {
        if (!installEvent) {
          return;
        }
        await installEvent.prompt();
        await installEvent.userChoice;
        setInstallEvent(null);
      },
    }),
    [installEvent, installed],
  );
}

declare global {
  interface Navigator {
    standalone?: boolean;
  }
}
