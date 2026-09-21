import jsQR from 'jsqr';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { parseCheckInCode } from '../checkinCode';
import { useGymAuth } from '../auth/GymAuthContext';
import { Alert, Button, Card, Field } from '../components/ui';

/** Ancho al que se reduce cada cuadro antes de buscar el QR: mas chico es mas rapido y alcanza. */
const FRAME_WIDTH = 640;
const SCAN_INTERVAL_MS = 150;

/**
 * Escaner del QR de la entrada, con la camara trasera.
 *
 * <p>Tambien hay un campo para pegar el codigo: sirve para probar en una
 * computadora sin camara y para el socio que no da permiso a la camara.
 */
export function ScanScreen({ onCode, onCancel }: { onCode: (token: string) => void; onCancel: () => void }) {
  const { slug } = useGymAuth();
  const videoRef = useRef<HTMLVideoElement>(null);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [hint, setHint] = useState<string | null>(null);
  const [pasted, setPasted] = useState('');
  const [pasteError, setPasteError] = useState<string | null>(null);

  useEffect(() => {
    let stopped = false;
    let stream: MediaStream | null = null;
    let timer: number | undefined;
    let lastRejected = '';
    const canvas = document.createElement('canvas');

    function scanFrame() {
      const video = videoRef.current;
      if (stopped || !video) {
        return;
      }
      if (video.readyState >= video.HAVE_ENOUGH_DATA && video.videoWidth > 0) {
        const scale = Math.min(1, FRAME_WIDTH / video.videoWidth);
        canvas.width = Math.round(video.videoWidth * scale);
        canvas.height = Math.round(video.videoHeight * scale);
        const context = canvas.getContext('2d', { willReadFrequently: true });
        if (context) {
          context.drawImage(video, 0, 0, canvas.width, canvas.height);
          const image = context.getImageData(0, 0, canvas.width, canvas.height);
          const found = jsQR(image.data, image.width, image.height, { inversionAttempts: 'dontInvert' });
          if (found && found.data !== lastRejected) {
            const parsed = parseCheckInCode(found.data, slug);
            if (parsed.ok) {
              stopped = true;
              onCode(parsed.token);
              return;
            }
            // Un QR que no es de este gimnasio: se avisa una vez y se sigue buscando.
            lastRejected = found.data;
            setHint(parsed.error);
          }
        }
      }
      timer = window.setTimeout(scanFrame, SCAN_INTERVAL_MS);
    }

    async function start() {
      if (!navigator.mediaDevices?.getUserMedia) {
        setCameraError('Este navegador no puede abrir la cámara acá. Probá pegando el código.');
        return;
      }
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
          audio: false,
        });
        if (stopped) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        const video = videoRef.current;
        if (video) {
          video.srcObject = stream;
          await video.play();
          scanFrame();
        }
      } catch {
        setCameraError('No pudimos abrir la cámara. Revisá que hayas dado el permiso, o pegá el código.');
      }
    }

    void start();

    return () => {
      stopped = true;
      window.clearTimeout(timer);
      stream?.getTracks().forEach((track) => track.stop());
    };
    // El escaner se arma una sola vez por pantalla: onCode y slug no cambian mientras esta abierto.
  }, []);

  function submitPasted(event: FormEvent) {
    event.preventDefault();
    const parsed = parseCheckInCode(pasted, slug);
    if (parsed.ok) {
      onCode(parsed.token);
    } else {
      setPasteError(parsed.error);
    }
  }

  return (
    <>
      <h1 className="text-3xl">Escaneá el QR</h1>
      <p className="mt-2 text-sm text-ink-soft">Apuntá la cámara al cartel de la entrada.</p>

      <div className="relative mt-6 aspect-square overflow-hidden rounded-2xl border border-borde bg-vidrio">
        <video ref={videoRef} playsInline muted className="size-full object-cover" />
        {!cameraError && (
          <span className="pointer-events-none absolute inset-8 rounded-2xl border-2 border-ladrillo-claro/70" aria-hidden />
        )}
      </div>

      <div className="mt-4 space-y-3">
        {cameraError && <Alert tone="info">{cameraError}</Alert>}
        {hint && <Alert>{hint}</Alert>}
      </div>

      <Card className="mt-6">
        <form className="space-y-4" onSubmit={submitPasted}>
          <Field
            label="¿No podés escanear? Pegá el código"
            value={pasted}
            onChange={(value) => {
              setPasted(value);
              setPasteError(null);
            }}
            placeholder="Link o código del QR"
          />
          {pasteError && <Alert>{pasteError}</Alert>}
          <Button type="submit" variant="secondary" disabled={pasted.trim().length === 0}>
            Usar este código
          </Button>
        </form>
      </Card>

      <button
        type="button"
        onClick={onCancel}
        className="mt-4 block w-full text-center text-xs text-ink-soft underline-offset-4 hover:underline"
      >
        Volver
      </button>
    </>
  );
}
