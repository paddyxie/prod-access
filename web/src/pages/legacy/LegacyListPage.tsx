import { api } from '../../api';
import { ListPage } from '../../components/layout';

export function LegacyListPage() {
  return <ListPage title="Legacy audit" subtitle="Original records preserved during the schema upgrade. Historical API requests and contracts that were never recorded remain unavailable."
    filters={[
      {name: 'executionId', label: 'Execution ID'},
      {name: 'sourceTable', label: 'Record type', options: [
        {value: 'runs', label: 'Executions'}, {value: 'tools', label: 'Tool definitions'}, {value: 'tasks', label: 'Workflow definitions'},
        {value: 'task_versions', label: 'Workflow versions'}, {value: 'tool_call_audit', label: 'Tool calls'}, {value: 'run_events', label: 'Execution events'},
      ]},
    ]} load={(f) => api.legacy.list(f)} columns={[
      {header: 'Type', render: (r) => r.sourceTable}, {header: 'ID', render: (r) => r.sourceId}, {header: 'Execution', render: (r) => r.executionId},
    ]} rowKey={(r) => `${r.sourceTable}:${r.sourceId}`} rowLink={(r) => `/legacy/${r.sourceTable}/${r.sourceId}`} emptyText="No archived records." />;
}
