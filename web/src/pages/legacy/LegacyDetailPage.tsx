import { Alert, Box } from '@mui/material';
import { useParams } from 'react-router';
import { api } from '../../api';
import { DetailPage, Section } from '../../components/layout';
import { useResource } from '../../hooks/useResource';
import { prettyJson } from '../../lib/format';

export function LegacyDetailPage() {
  const {table = '', id = ''} = useParams();
  const record = useResource(`legacy:${table}:${id}`, () => api.legacy.get(table, id));
  const related = useResource(`legacy-related:${record.data?.executionId}`, () => record.data?.executionId ? api.legacy.list({executionId: record.data.executionId}) : Promise.resolve([]));
  return <DetailPage back={{to: '/legacy', label: 'Legacy audit'}} title={`Archived ${id}`} error={record.error} loading={!record.data && !record.error}>
    <Alert severity="info">This is original legacy evidence. API request/response details missing from the old implementation cannot be reconstructed.</Alert>
    {record.data && <Section title="Original record"><Box component="pre" sx={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}>{prettyJson(record.data.sourceJson)}</Box></Section>}
    {related.data?.filter((r) => r.sourceTable !== table || r.sourceId !== id).map((r) => <Section key={`${r.sourceTable}:${r.sourceId}`} title={`${r.sourceTable} / ${r.sourceId}`}>
      <Box component="pre" sx={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}>{prettyJson(r.sourceJson)}</Box>
    </Section>)}
  </DetailPage>;
}
