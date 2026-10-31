import { api } from '../../api';
import { ListPage } from '../../components/layout';
import { clientOptions, taskOptions } from '../../lib/options';
import { runColumns } from '../../components/run/runColumns';

export function ApprovalListPage() {
  return (
    <ListPage
      title="Approvals"
      subtitle="Runs that write data wait here until someone other than the submitter approves them."
      filters={[
        { name: 'taskId', label: 'Task', options: taskOptions },
        { name: 'clientId', label: 'Client', options: clientOptions },
        { name: 'submittedBy', label: 'Submitted by' },
      ]}
      load={(f) => api.runs.list({ ...f, status: 'PENDING_APPROVAL' })}
      columns={runColumns.filter((c) => c.header !== 'Status' && c.header !== 'Duration')}
      rowKey={(r) => r.id}
      rowLink={(r) => `/runs/${r.id}`}
      emptyText="Nothing is waiting for approval."
      pollMs={5000}
    />
  );
}
