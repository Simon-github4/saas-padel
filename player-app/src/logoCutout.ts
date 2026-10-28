/**
 * Recorte del fondo blanco de un logo, para la portada "logo del club".
 *
 * <p>Casi todos los logos de perfil están hechos sobre un cuadrado blanco. En
 * vez de envolverlo en una placa o taparlo con una luz, se borra el blanco que
 * toca los bordes de la imagen y el logo queda suelto sobre el fondo de la
 * portada, en la paleta clara y en la oscura.
 *
 * <p>Se borra solo el blanco conectado con el borde (un relleno desde el
 * perímetro), no todo el blanco: el relleno de las letras, encerrado por su
 * contorno, se queda. Tampoco el de una letra que el borde de la imagen corta
 * al medio: toca el borde, pero es una zona angosta. Los píxeles de la orilla, que la compresión mezcló con
 * el blanco, pasan a semitransparentes y se les saca ese blanco del color; si
 * no, en la paleta oscura el logo quedaba con un filo claro alrededor.
 */

/** Hasta acá de "no blanco" (255 menos el canal más bajo) el píxel se borra entero. */
const CLEAR = 24;
/** Desde acá el píxel ya es del logo; entre CLEAR y esto queda semitransparente. */
const SOLID = 96;
/**
 * Ancho mínimo de una zona blanca para tomarla como fondo, como parte del lado
 * de la imagen: el radio del círculo más grande que entra en ella sin tocar
 * tinta. El tamaño no alcanzaba para separarlas: en el logo de Sanset el
 * relleno de "ET", cortado por el borde, ocupa el 3,5% de la imagen y un hueco
 * de fondo entre la paleta y la cabeza, el 2,4%. El ancho sí: los rellenos de
 * letra son trazos, de hasta 43 px de radio en 640; el fondo, desde 65.
 */
const MIN_BACKGROUND_RADIUS = 0.085;
/** Proporción del borde que tiene que ser blanca para recortar. */
const WHITE_EDGE = 0.5;

export type Cutout = {
  /** Si se recortó: false cuando el logo no está sobre blanco (una foto, un fondo de color). */
  cut: boolean;
  /** Color de marca del logo (el promedio de sus píxeles con color), o null si es gris. */
  tint: [number, number, number] | null;
};

function distanceFromWhite(data: Uint8ClampedArray, i: number): number {
  return 255 - Math.min(data[i], data[i + 1], data[i + 2]);
}

/**
 * Distancia aproximada (en píxeles) de cada píxel a la tinta más cercana, con
 * dos pasadas de chamfer 3-4. El borde de la imagen no cuenta como tinta: el
 * fondo sigue más allá.
 */
function inkDistance(data: Uint8ClampedArray, width: number, height: number): Float32Array {
  const dist = new Float32Array(width * height);
  for (let p = 0; p < dist.length; p++) {
    dist[p] = distanceFromWhite(data, p * 4) >= SOLID ? 0 : Infinity;
  }
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const p = y * width + x;
      if (dist[p] === 0) {
        continue;
      }
      const left = x > 0 ? dist[p - 1] + 3 : Infinity;
      const up = y > 0 ? dist[p - width] + 3 : Infinity;
      const upLeft = y > 0 && x > 0 ? dist[p - width - 1] + 4 : Infinity;
      const upRight = y > 0 && x < width - 1 ? dist[p - width + 1] + 4 : Infinity;
      dist[p] = Math.min(dist[p], left, up, upLeft, upRight);
    }
  }
  for (let y = height - 1; y >= 0; y--) {
    for (let x = width - 1; x >= 0; x--) {
      const p = y * width + x;
      if (dist[p] === 0) {
        continue;
      }
      const right = x < width - 1 ? dist[p + 1] + 3 : Infinity;
      const down = y < height - 1 ? dist[p + width] + 3 : Infinity;
      const downRight = y < height - 1 && x < width - 1 ? dist[p + width + 1] + 4 : Infinity;
      const downLeft = y < height - 1 && x > 0 ? dist[p + width - 1] + 4 : Infinity;
      dist[p] = Math.min(dist[p], right, down, downRight, downLeft);
    }
  }
  for (let p = 0; p < dist.length; p++) {
    dist[p] /= 3;
  }
  return dist;
}

/** Recorta el blanco del borde sobre los mismos píxeles. Exportada para los tests. */
export function cutWhiteBackground(data: Uint8ClampedArray, width: number, height: number): Cutout {
  const perimeter: number[] = [];
  for (let x = 0; x < width; x++) {
    perimeter.push(x, (height - 1) * width + x);
  }
  for (let y = 1; y < height - 1; y++) {
    perimeter.push(y * width, y * width + width - 1);
  }
  const whiteEdge = perimeter.filter((p) => distanceFromWhite(data, p * 4) < CLEAR).length;
  const cut = whiteEdge / perimeter.length >= WHITE_EDGE;

  if (cut) {
    // Cada zona blanca que toca el borde, por separado. Las letras cortadas por
    // el borde de la imagen también lo tocan, pero su relleno es angosto y está
    // separado del fondo por el contorno: solo se borran las zonas anchas.
    const ink = inkDistance(data, width, height);
    const zone = new Int32Array(width * height).fill(-1);
    const zones: number[][] = [];
    for (const start of perimeter) {
      if (zone[start] >= 0 || distanceFromWhite(data, start * 4) >= SOLID) {
        continue;
      }
      const id = zones.length;
      const pixels: number[] = [];
      const stack = [start];
      let radius = 0;
      zone[start] = id;
      while (stack.length > 0) {
        const p = stack.pop()!;
        pixels.push(p);
        radius = Math.max(radius, ink[p]);
        const x = p % width;
        const neighbours = [x > 0 ? p - 1 : -1, x < width - 1 ? p + 1 : -1, p - width, p + width];
        for (const n of neighbours) {
          if (n >= 0 && n < width * height && zone[n] < 0 && distanceFromWhite(data, n * 4) < SOLID) {
            zone[n] = id;
            stack.push(n);
          }
        }
      }
      if (radius >= Math.max(width, height) * MIN_BACKGROUND_RADIUS) {
        zones.push(pixels);
      }
    }

    for (const pixels of zones) {
      for (const p of pixels) {
        const i = p * 4;
        const d = distanceFromWhite(data, i);
        const alpha = d <= CLEAR ? 0 : (d - CLEAR) / (SOLID - CLEAR);
        if (alpha > 0) {
          // Sacarle al color el blanco con el que se mezcló: c = a·logo + (1-a)·255.
          for (let c = 0; c < 3; c++) {
            data[i + c] = Math.max(0, Math.round((data[i + c] - 255 * (1 - alpha)) / alpha));
          }
        }
        data[i + 3] = Math.round(data[i + 3] * alpha);
      }
    }
  }

  let r = 0;
  let g = 0;
  let b = 0;
  let count = 0;
  for (let i = 0; i < data.length; i += 4) {
    const max = Math.max(data[i], data[i + 1], data[i + 2]);
    const min = Math.min(data[i], data[i + 1], data[i + 2]);
    if (data[i + 3] > 200 && max - min > 60) {
      r += data[i];
      g += data[i + 1];
      b += data[i + 2];
      count++;
    }
  }
  const tint: [number, number, number] | null =
    count > (width * height) / 100 ? [Math.round(r / count), Math.round(g / count), Math.round(b / count)] : null;

  return { cut, tint };
}

/** Lado máximo con el que se procesa: los logos de perfil andan por 640. */
const MAX_SIDE = 1024;

/**
 * Baja el logo, lo recorta y devuelve un object URL del PNG resultante (hay
 * que liberarlo con URL.revokeObjectURL). Rechaza si no se puede leer: una URL
 * externa sin CORS, por ejemplo; ahí la portada muestra la imagen tal cual.
 */
export async function loadLogoCutout(url: string): Promise<Cutout & { src: string }> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Logo ${response.status}`);
  }
  const bitmap = await createImageBitmap(await response.blob());
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) {
    throw new Error('Sin canvas');
  }
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
  const result = cutWhiteBackground(pixels.data, canvas.width, canvas.height);
  context.putImageData(pixels, 0, 0);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
  if (!blob) {
    throw new Error('Sin PNG');
  }
  return { ...result, src: URL.createObjectURL(blob) };
}
