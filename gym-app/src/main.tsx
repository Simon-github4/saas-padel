import '@fontsource-variable/archivo';
import '@fontsource/bebas-neue';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import './index.css';
import { GymApp } from './pages/GymApp';
import { setupGymPwa } from './pwa';

setupGymPwa();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        {/* Una sola ruta para toda la app: ver GymApp. */}
        <Route path="/gym/:slug/*" element={<GymApp />} />
        <Route
          path="*"
          element={
            <p className="p-6 text-center text-sm text-ink-soft">
              Escaneá el QR de la entrada de tu gimnasio para abrir tu cuenta.
            </p>
          }
        />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
