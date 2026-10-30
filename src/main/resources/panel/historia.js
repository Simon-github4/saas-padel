/*
 * Historia de turnos libres del panel del club (ver StoryDialog).
 *
 * Dibuja la historia en un canvas de 1080 x 1920 y deja el JPG listo para compartir o
 * descargar. Se sirve aparte (PanelStoryController) con cache larga y la version en la
 * URL: el navegador lo baja una sola vez, y no viaja con cada apertura del dialogo.
 */
(() => {
  if (window.padelStory) {
    return;
  }
  const W = 1080, H = 1920;
  const FONT = '"Archivo Variable", system-ui, sans-serif';

  // Por URL: la plantilla y el logo se sirven en direcciones que cambian solo cuando
  // cambia la imagen, asi que una vez cargados se reusan sin volver a pedirlos.
  const images = new Map();
  const image = (src) => {
    if (!images.has(src)) {
      images.set(src, new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => resolve(img);
        img.onerror = () => { images.delete(src); reject(); };
        img.src = src;
      }));
    }
    return images.get(src);
  };
  const fonts = () => document.fonts
      ? Promise.all([document.fonts.load('800 100px "Archivo Variable"'),
          document.fonts.load('900 100px "Archivo Variable"')]).catch(() => null)
      : Promise.resolve();
  const font = (ctx, weight, size) => { ctx.font = `${weight} ${Math.round(size)}px ${FONT}`; };
  const fit = (ctx, text, weight, size, maxWidth) => {
    font(ctx, weight, size);
    const width = ctx.measureText(text).width;
    if (width > maxWidth) { size = size * maxWidth / width; font(ctx, weight, size); }
    return size;
  };
  const mix = (hex, amount) => {
    const n = parseInt(hex.slice(1), 16);
    const c = [n >> 16, (n >> 8) & 255, n & 255].map((v) => Math.round(v * (1 - amount)));
    return `rgb(${c[0]}, ${c[1]}, ${c[2]})`;
  };
  const cover = (ctx, img) => {
    const scale = Math.max(W / img.naturalWidth, H / img.naturalHeight);
    const w = img.naturalWidth * scale, h = img.naturalHeight * scale;
    ctx.drawImage(img, (W - w) / 2, (H - h) / 2, w, h);
  };
  // Sin plantilla: el color del club arriba, fundido a negro, y las lineas de una
  // cancha vista desde el fondo.
  const plain = (ctx, color) => {
    const g = ctx.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, mix(color, 0.25));
    g.addColorStop(0.45, mix(color, 0.8));
    g.addColorStop(1, '#050608');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);
    ctx.save();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.10)';
    ctx.lineWidth = 6;
    ctx.beginPath();
    ctx.moveTo(250, 1560); ctx.lineTo(830, 1560); ctx.lineTo(1080, 1920);
    ctx.moveTo(250, 1560); ctx.lineTo(0, 1920);
    ctx.moveTo(540, 1560); ctx.lineTo(540, 1920);
    ctx.moveTo(170, 1675); ctx.lineTo(910, 1675);
    ctx.stroke();
    ctx.restore();
  };
  const pill = (ctx, x, y, w, h, fill) => {
    ctx.save();
    ctx.shadowColor = 'rgba(0, 0, 0, 0.28)';
    ctx.shadowBlur = 16;
    ctx.shadowOffsetY = 5;
    ctx.fillStyle = fill;
    ctx.beginPath();
    ctx.roundRect(x, y, w, h, h / 2);
    ctx.fill();
    ctx.restore();
  };
  const text = (ctx, value, x, y, color, glow) => {
    ctx.save();
    if (glow) { ctx.shadowColor = 'rgba(0, 0, 0, 0.6)'; ctx.shadowBlur = 18; ctx.shadowOffsetY = 3; }
    ctx.fillStyle = color;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(value, x, y);
    ctx.restore();
  };
  // La marca de TurnosPadel con el link de reservas, abajo a la derecha. No va pegada
  // al borde: los ultimos ~200 px de una historia los tapa la barra de "Enviar
  // mensaje" de Instagram.
  const brand = (ctx, logo, link) => {
    const h = 76, pad = 18, icon = 48, right = W - 40, bottom = H - 210;
    const size = fit(ctx, link, 700, 30, W * 0.62);
    const w = pad + icon + 14 + ctx.measureText(link).width + pad * 1.4;
    const x = right - w, y = bottom - h;
    ctx.save();
    ctx.fillStyle = 'rgba(0, 0, 0, 0.55)';
    ctx.beginPath();
    ctx.roundRect(x, y, w, h, h / 2);
    ctx.fill();
    ctx.restore();
    if (logo) { ctx.drawImage(logo, x + pad, y + (h - icon) / 2, icon, icon); }
    ctx.save();
    font(ctx, 700, size);
    ctx.fillStyle = '#ffffff';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    ctx.fillText(link, x + pad + icon + 14, y + h / 2 + 1);
    ctx.restore();
  };
  const encode = (canvas, quality = 0.92) =>
      new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
  const download = (file) => {
    const url = URL.createObjectURL(file);
    const a = document.createElement('a');
    a.href = url;
    a.download = file.name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  };
  // Un link dibujado no se puede tocar: el que se toca es el que se pega en el sticker
  // "Enlace" de Instagram o en el texto del estado de WhatsApp. Se copia sin esperar,
  // todavia dentro del toque, como necesita compartir.
  const copyLink = (canvas) => {
    if (navigator.clipboard) { navigator.clipboard.writeText(canvas._spec.url).catch(() => null); }
  };
  const fileOf = async (canvas) => {
    if (canvas._file) { return canvas._file; }
    const blob = await encode(canvas);
    return new File([blob], canvas._spec.file, {type: 'image/jpeg'});
  };

  /*
   * La plantilla achicada antes de subirla: lo justo para cubrir la historia, en JPG.
   * Una foto del celular de varios MB sale en unos cientos de KB, que es lo que se
   * gasta en datos. Ademas sale derecha: el navegador aplica la rotacion que el
   * celular guarda aparte en la foto, y el servidor no. Si algo falla, va la original
   * y la achica el servidor (StoryTemplateImages).
   */
  const shrink = async (file) => {
    try {
      const bitmap = await createImageBitmap(file);
      const scale = Math.min(1, Math.max(W / bitmap.width, H / bitmap.height));
      const width = Math.round(bitmap.width * scale), height = Math.round(bitmap.height * scale);
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = '#000000';
      ctx.fillRect(0, 0, width, height);
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(bitmap, 0, 0, width, height);
      bitmap.close();
      const blob = await encode(canvas, 0.9);
      return blob && (scale < 1 || blob.size < file.size) ? blob : file;
    } catch (e) {
      return file;
    }
  };

  window.padelStory = {
    async render(canvas, spec) {
      canvas._spec = spec;
      canvas._file = null;
      const token = (canvas._token || 0) + 1;
      canvas._token = token;
      const src = spec.template ? canvas.getAttribute('data-template') : null;
      const [img, logo] = await Promise.all([src ? image(src).catch(() => null) : null,
          image(spec.logo).catch(() => null), fonts()]);
      if (canvas._token !== token) { return; }

      canvas.width = W;
      canvas.height = H;
      const ctx = canvas.getContext('2d');
      ctx.clearRect(0, 0, W, H);
      if (img) { cover(ctx, img); } else { plain(ctx, spec.color); }

      const top = (img ? spec.top : 9) / 100 * H;
      const bottom = (img ? spec.bottom : 74) / 100 * H;
      const withTitle = !img || spec.title;
      const times = spec.times.length ? spec.times : [spec.empty];
      const cols = times.length <= 7 ? 1 : times.length <= 14 ? 2 : 3;
      const rows = Math.ceil(times.length / cols);
      const units = (withTitle ? 2.1 : 0) + 1.25 + 0.45 + rows + (rows - 1) * 0.3;
      const u = Math.min((bottom - top) / units, 140);
      let y = top + ((bottom - top) - units * u) / 2;

      const boxed = spec.style === 'PILLS' || spec.style === 'COLOR';
      const ink = spec.style === 'DARK' ? '#111111' : '#ffffff';
      const glow = spec.style !== 'DARK';

      if (withTitle) {
        const size = fit(ctx, 'TURNOS', 900, u * 1.05, W * 0.8);
        text(ctx, 'TURNOS', W / 2, y + u * 0.5, ink, glow);
        fit(ctx, 'DISPONIBLES', 900, size, W * 0.86);
        text(ctx, 'DISPONIBLES', W / 2, y + u * 1.45, boxed || spec.style === 'LIGHT' ? spec.accent : ink, glow);
        y += u * 2.1;
      }

      const daySize = fit(ctx, spec.day, 800, u * 0.66, W * 0.7);
      if (boxed) {
        const w = Math.min(ctx.measureText(spec.day).width + u * 1.1, W * 0.84);
        pill(ctx, (W - w) / 2, y, w, u * 1.1, '#ffffff');
        text(ctx, spec.day, W / 2, y + u * 0.57, spec.style === 'COLOR' ? spec.color : '#111111', false);
      } else {
        font(ctx, 800, daySize * 1.1);
        text(ctx, spec.day, W / 2, y + u * 0.57, ink, glow);
      }
      y += u * 1.7;

      const gap = W * 0.04;
      const width = cols === 1 ? W * 0.56 : cols === 2 ? W * 0.38 : W * 0.27;
      const left = (W - cols * width - (cols - 1) * gap) / 2;
      times.forEach((value, i) => {
        const col = Math.floor(i / rows), row = i % rows;
        const x = left + col * (width + gap);
        const rowY = y + row * u * 1.3;
        if (boxed) {
          pill(ctx, x, rowY, width, u * 0.92, spec.style === 'COLOR' ? spec.color : '#ffffff');
        }
        fit(ctx, value, 800, u * 0.6, width * 0.84);
        text(ctx, value, x + width / 2, rowY + u * 0.48,
            boxed ? (spec.style === 'COLOR' ? '#ffffff' : '#111111') : ink, !boxed && glow);
      });

      if (!img) {
        fit(ctx, spec.club.toUpperCase(), 800, 64, W * 0.84);
        text(ctx, spec.club.toUpperCase(), W / 2, H * 0.79, '#ffffff', true);
      }
      brand(ctx, logo, spec.link);

      // El JPG recien cuando se deja de mover la franja: armarlo en cada paso del
      // arrastre era trabajo tirado. Si tocan Compartir antes, fileOf lo arma ahi.
      clearTimeout(canvas._encoding);
      canvas._encoding = setTimeout(async () => {
        const blob = await encode(canvas);
        if (canvas._token === token && blob) {
          canvas._file = new File([blob], spec.file, {type: 'image/jpeg'});
        }
      }, 300);
    },

    // La franja se ve moverse mientras se arrastra (un dibujo por cuadro de pantalla,
    // no uno por cada evento); al soltar la guarda el servidor.
    bindRange(input, canvas, key) {
      let frame = 0;
      input.addEventListener('input', () => {
        if (!canvas._spec || frame) { return; }
        frame = requestAnimationFrame(() => {
          frame = 0;
          window.padelStory.render(canvas, {...canvas._spec, [key]: Number(input.value)});
        });
      });
    },

    // Cambia lo que manda el vaadin-upload por la plantilla ya achicada (ver shrink).
    bindUpload(upload) {
      upload.addEventListener('upload-request', (event) => {
        const {xhr, file, formData} = event.detail;
        event.preventDefault();
        shrink(file).then((body) => {
          if (formData) {
            const form = new FormData();
            form.append(file.formDataName, body, body === file ? file.name : 'plantilla.jpg');
            xhr.send(form);
          } else {
            xhr.send(body);
          }
        });
      });
    },

    canShare() {
      const mobile = navigator.userAgentData ? navigator.userAgentData.mobile
          : /Mobi|Android|iPhone|iPad/.test(navigator.userAgent);
      try {
        return mobile && !!navigator.canShare
            && navigator.canShare({files: [new File([''], 'x.jpg', {type: 'image/jpeg'})]});
      } catch (e) {
        return false;
      }
    },

    async share(canvas) {
      if (!canvas._spec) { return; }
      copyLink(canvas);
      const file = await fileOf(canvas);
      try {
        await navigator.share({files: [file]});
      } catch (e) {
        // Cancelar el menu de compartir tambien llega aca: no es un error.
        if (e && e.name !== 'AbortError') { download(file); }
      }
    },

    async download(canvas) {
      if (!canvas._spec) { return; }
      copyLink(canvas);
      download(await fileOf(canvas));
    }
  };
})();
