import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { ClubPage } from './pages/ClubPage';
import { ManagePage } from './pages/ManagePage';
import { SharePage } from './pages/SharePage';
import { Landing } from './pages/Landing';
import { SearchPage } from './pages/SearchPage';
import { LoginPage } from './pages/LoginPage';
import { AccountPage } from './pages/AccountPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { PrivacyPage } from './pages/legal/PrivacyPage';
import { TermsPage } from './pages/legal/TermsPage';
import '@fontsource/bebas-neue';
import '@fontsource-variable/archivo/wdth.css';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<Landing />} />
          {/* Buscar en todos los clubes: la entrada de quien no eligió dónde jugar. */}
          <Route path="/buscar" element={<SearchPage />} />
          <Route path="/club/:slug" element={<ClubPage />} />
          {/* El link del WhatsApp: abrirlo ya confirma la reserva. */}
          <Route path="/confirm/:token" element={<ManagePage mode="confirm" />} />
          <Route path="/manage/:token" element={<ManagePage mode="manage" />} />
          {/* Link de solo lectura que el jugador le manda a los demás. */}
          <Route path="/turno/:token" element={<SharePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password/:token" element={<ResetPasswordPage />} />
          <Route path="/account" element={<AccountPage />} />
          <Route path="/privacidad" element={<PrivacyPage />} />
          <Route path="/terminos" element={<TermsPage />} />
          {/* Cualquier link roto (o de jugador, mal copiado) cae en la landing
              comercial: por eso esa página deja "Buscar cancha" visible. */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
