import { api } from '../../api';
import { ListPage } from '../../components/layout';
import { formatTime } from '../../lib/format';
import { roleOptions } from '../../lib/options';

export function AuditListPage() {
  return <ListPage title="Tool audit" subtitle="Find who called which API, in which service and workflow execution. Open a record for request/response evidence. Latest 500 matches."
    filters={[
      {name: 'actor', label: 'Actor'},
      {name: 'service', label: 'Service', options: async () => (await api.catalog.services()).map((s) => ({value: s.code, label: s.name}))},
      {name: 'role', label: 'System / Role', options: roleOptions}, {name: 'tool', label: 'Tool name'},
      {name: 'status', label: 'State', options: ['RUNNING','SUCCEEDED','FAILED','UNKNOWN'].map((s) => ({value: s, label: s}))},
      {name: 'from', label: 'From', type: 'datetime-local'}, {name: 'to', label: 'To', type: 'datetime-local'},
    ]} load={(f) => api.audit({...f, from: f.from ? new Date(f.from).toISOString() : '', to: f.to ? new Date(f.to).toISOString() : ''})}
    columns={[
      {header: 'Started', render: (c) => formatTime(c.startedAt)}, {header: 'Actor', render: (c) => c.actor}, {header: 'Service', render: (c) => c.service},
      {header: 'System / Role', render: (c) => c.role}, {header: 'Tool', render: (c) => `${c.tool} v${c.ver}`}, {header: 'State', render: (c) => c.status},
      {header: 'Execution', render: (c) => c.executionId},
    ]} rowKey={(c) => c.id} rowLink={(c) => `/runs/${c.executionId}?invocation=${c.id}`} emptyText="No tool invocations match." />;
}
