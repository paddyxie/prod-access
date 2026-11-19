import { createBrowserRouter, Navigate } from 'react-router';
import { AppShell } from './components/layout/AppShell';
import { NotFoundPage } from './pages/NotFoundPage';
import { AdHocRunFormPage } from './pages/runs/AdHocRunFormPage';
import { RunDetailPage } from './pages/runs/RunDetailPage';
import { RunListPage } from './pages/runs/RunListPage';
import { TaskDetailPage } from './pages/tasks/TaskDetailPage';
import { TaskFormPage } from './pages/tasks/TaskFormPage';
import { TaskListPage } from './pages/tasks/TaskListPage';
import { TaskRunFormPage } from './pages/tasks/TaskRunFormPage';
import { ToolCallFormPage } from './pages/tools/ToolCallFormPage';
import { ToolFormPage } from './pages/tools/ToolFormPage';
import { ToolListPage } from './pages/tools/ToolListPage';

// URL scheme: /<section> list · /<section>/new create · /<section>/:id detail · /<section>/:id/edit edit.
export const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/runs" replace /> },
      { path: 'runs', element: <RunListPage /> },
      { path: 'runs/new', element: <AdHocRunFormPage /> },
      { path: 'runs/:id', element: <RunDetailPage /> },
      { path: 'tasks', element: <TaskListPage /> },
      { path: 'tasks/new', element: <TaskFormPage /> },
      { path: 'tasks/:id', element: <TaskDetailPage /> },
      { path: 'tasks/:id/edit', element: <TaskFormPage /> },
      { path: 'tasks/:id/run', element: <TaskRunFormPage /> },
      { path: 'tools', element: <ToolListPage /> },
      { path: 'tools/new', element: <ToolFormPage /> },
      { path: 'tools/:id/edit', element: <ToolFormPage /> },
      { path: 'tools/:id/call', element: <ToolCallFormPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
