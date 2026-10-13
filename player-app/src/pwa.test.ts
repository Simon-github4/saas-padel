import { describe, expect, it } from 'vitest';
import { isInAppBrowser } from './pwa';

/** User agents reales, recortados a lo que importa. */
const UA = {
  chromeAndroid:
    'Mozilla/5.0 (Linux; Android 14; SM-A546E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36',
  samsungInternet:
    'Mozilla/5.0 (Linux; Android 14; SM-A546E) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/26.0 Chrome/122.0.0.0 Mobile Safari/537.36',
  safariIphone:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1',
  chromeIphone:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/129.0.6668.69 Mobile/15E148 Safari/604.1',
  instagramIphone:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 350.0.0.29.106 (iPhone15,2; iOS 17_6; es_AR; es)',
  instagramAndroid:
    'Mozilla/5.0 (Linux; Android 14; SM-A546E Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/129.0.6668.81 Mobile Safari/537.36 Instagram 350.0.0.42.89 Android',
  facebookIphone:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 [FBAN/FBIOS;FBAV/482.0.0.42.97;FBBV/650000000;FBDV/iPhone15,2;FBMD/iPhone;FBSN/iOS;FBSV/17.6]',
  tiktokIphone:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 musical_ly_36.5.0 JsSdk/2.0 NetType/WIFI Channel/App Store',
  threadsIphone:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Barcelona 350.0.0.29.106 (iPhone15,2; iOS 17_6; es_AR)',
  chromeCompu:
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
};

describe('isInAppBrowser', () => {
  it('reconoce el navegador de adentro de Instagram, Facebook, TikTok y Threads', () => {
    expect(isInAppBrowser(UA.instagramIphone)).toBe(true);
    expect(isInAppBrowser(UA.instagramAndroid)).toBe(true);
    expect(isInAppBrowser(UA.facebookIphone)).toBe(true);
    expect(isInAppBrowser(UA.tiktokIphone)).toBe(true);
    expect(isInAppBrowser(UA.threadsIphone)).toBe(true);
  });

  it('no confunde a los navegadores de verdad, que son los que instalan', () => {
    // Si alguno cayera acá, se quedaría sin los pasos ni el botón de instalar.
    expect(isInAppBrowser(UA.chromeAndroid)).toBe(false);
    expect(isInAppBrowser(UA.samsungInternet)).toBe(false);
    expect(isInAppBrowser(UA.safariIphone)).toBe(false);
    expect(isInAppBrowser(UA.chromeIphone)).toBe(false);
    expect(isInAppBrowser(UA.chromeCompu)).toBe(false);
  });
});
