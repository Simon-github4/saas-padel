import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ClubPage } from './pages/ClubPage';
import { ManagePage } from './pages/ManagePage';
import { Landing } from './pages/Landing';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/club/:slug" element={<ClubPage />} />
        {/* El link del WhatsApp: abrirlo ya confirma la reserva. */}
        <Route path="/confirm/:token" element={<ManagePage mode="confirm" />} />
        <Route path="/manage/:token" element={<ManagePage mode="manage" />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
