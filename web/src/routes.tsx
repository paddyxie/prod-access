import { AuditListPage } from './pages/audit/AuditListPage';
import { LegacyListPage } from './pages/legacy/LegacyListPage';
import { LegacyDetailPage } from './pages/legacy/LegacyDetailPage';
import { ServiceListPage } from './pages/catalog/ServiceListPage';
import { ServiceFormPage } from './pages/catalog/ServiceFormPage';
import { SystemListPage } from './pages/catalog/SystemListPage';
import { SystemFormPage } from './pages/catalog/SystemFormPage';
import { createBrowserRouter, Navigate } from 'react-router';
import { AppShell } from './components/layout/AppShell';
import { NotFoundPage } from './pages/NotFoundPage';
import { AdHocRunFormPage } from './pages/runs/AdHocRunFormPage';
import { RunDetailPage } from './pages/runs/RunDetailPage';
import { RunListPage } from './pages/runs/RunListPage';
import { WorkflowDetailPage } from './pages/workflows/WorkflowDetailPage';
import { WorkflowFormPage } from './pages/workflows/WorkflowFormPage';
import { WorkflowListPage } from './pages/workflows/WorkflowListPage';
import { WorkflowRunFormPage } from './pages/workflows/WorkflowRunFormPage';
import { ToolCallFormPage } from './pages/tools/ToolCallFormPage';
import { ToolFormPage } from './pages/tools/ToolFormPage';
import { ToolListPage } from './pages/tools/ToolListPage';
import { ToolDetailPage } from './pages/tools/ToolDetailPage';

// URL scheme: /<section> list · /<section>/new create · /<section>/:id detail · /<section>/:id/edit edit.
export const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/runs" replace /> },
      { path: 'runs', element: <RunListPage /> },
      { path: 'runs/new', element: <AdHocRunFormPage /> },
      { path: 'runs/:id', element: <RunDetailPage /> },
      { path: 'workflows', element: <WorkflowListPage /> },
      { path: 'workflows/new', element: <WorkflowFormPage /> },
      { path: 'workflows/:id', element: <WorkflowDetailPage /> },
      { path: 'workflows/:id/edit', element: <WorkflowFormPage /> },
      { path: 'workflows/:id/run', element: <WorkflowRunFormPage /> },
      { path: 'audit', element: <AuditListPage /> },
      { path: 'legacy', element: <LegacyListPage /> },
      { path: 'legacy/:table/:id', element: <LegacyDetailPage /> },
      { path: 'services', element: <ServiceListPage /> },
      { path: 'services/new', element: <ServiceFormPage /> },
      { path: 'systems', element: <SystemListPage /> },
      { path: 'systems/new', element: <SystemFormPage /> },
      { path: 'tools', element: <ToolListPage /> },
      { path: 'tools/new', element: <ToolFormPage /> },
      { path: 'tools/:id', element: <ToolDetailPage /> },
      { path: 'tools/:id/edit', element: <ToolFormPage /> },
      { path: 'tools/:id/call', element: <ToolCallFormPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
