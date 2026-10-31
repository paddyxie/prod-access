import { createBrowserRouter, Navigate } from 'react-router';
import { AppShell } from './components/layout/AppShell';
import { ApiFormPage } from './pages/apis/ApiFormPage';
import { ApiListPage } from './pages/apis/ApiListPage';
import { ApprovalListPage } from './pages/approvals/ApprovalListPage';
import { ClientFormPage } from './pages/clients/ClientFormPage';
import { ClientListPage } from './pages/clients/ClientListPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { AdHocRunFormPage } from './pages/runs/AdHocRunFormPage';
import { RunDetailPage } from './pages/runs/RunDetailPage';
import { RunListPage } from './pages/runs/RunListPage';
import { TaskDetailPage } from './pages/tasks/TaskDetailPage';
import { TaskFormPage } from './pages/tasks/TaskFormPage';
import { TaskListPage } from './pages/tasks/TaskListPage';
import { TaskRunFormPage } from './pages/tasks/TaskRunFormPage';

// URL scheme: /<section> list · /<section>/new create · /<section>/:id detail · /<section>/:id/edit edit.
export const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/runs" replace /> },
      { path: 'runs', element: <RunListPage /> },
      { path: 'runs/new', element: <AdHocRunFormPage /> },
      { path: 'runs/:id', element: <RunDetailPage /> },
      { path: 'approvals', element: <ApprovalListPage /> },
      { path: 'tasks', element: <TaskListPage /> },
      { path: 'tasks/new', element: <TaskFormPage /> },
      { path: 'tasks/:id', element: <TaskDetailPage /> },
      { path: 'tasks/:id/edit', element: <TaskFormPage /> },
      { path: 'tasks/:id/run', element: <TaskRunFormPage /> },
      { path: 'clients', element: <ClientListPage /> },
      { path: 'clients/new', element: <ClientFormPage /> },
      { path: 'clients/:id/edit', element: <ClientFormPage /> },
      { path: 'apis', element: <ApiListPage /> },
      { path: 'apis/new', element: <ApiFormPage /> },
      { path: 'apis/:id/edit', element: <ApiFormPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
