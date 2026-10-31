import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { isLoggedIn } from './api/auth';
import { Layout } from './components/Layout';
import DocumentDetailPage from './pages/documents/DocumentDetailPage';
import DocumentsPage from './pages/documents/DocumentsPage';
import ExamDetailPage from './pages/exams/ExamDetailPage';
import ExamsPage from './pages/exams/ExamsPage';
import LabelsPage from './pages/LabelsPage';
import LoginPage from './pages/LoginPage';
import MaterialDetailPage from './pages/materials/MaterialDetailPage';
import MaterialsPage from './pages/materials/MaterialsPage';
import PrintDocumentPage from './pages/PrintDocumentPage';
import RulesPage from './pages/rules/RulesPage';
import SettingsPage from './pages/SettingsPage';
import StudioPage from './pages/studio/StudioPage';

function RequireLogin({ children }: { children: ReactNode }) {
  const location = useLocation();
  if (!isLoggedIn()) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/print/documents/:id"
        element={
          <RequireLogin>
            <PrintDocumentPage />
          </RequireLogin>
        }
      />
      <Route
        element={
          <RequireLogin>
            <Layout />
          </RequireLogin>
        }
      >
        <Route path="/studio" element={<StudioPage />} />
        <Route path="/studio/:conversationId" element={<StudioPage />} />
        <Route path="/materials" element={<MaterialsPage />} />
        <Route path="/materials/:id" element={<MaterialDetailPage />} />
        <Route path="/labels" element={<LabelsPage />} />
        <Route path="/exams" element={<ExamsPage />} />
        <Route path="/exams/:materialId" element={<ExamDetailPage />} />
        <Route path="/rules" element={<RulesPage />} />
        <Route path="/documents" element={<DocumentsPage />} />
        <Route path="/documents/:id" element={<DocumentDetailPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/studio" replace />} />
    </Routes>
  );
}
