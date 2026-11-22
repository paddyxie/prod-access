import ApiOutlined from '@mui/icons-material/ApiOutlined';
import EditOutlined from '@mui/icons-material/EditOutlined';
import { Alert, Box, Button, Chip, Stack, Typography } from '@mui/material';
import { Link as RouterLink, useParams, useSearchParams } from 'react-router';
import { api, type Tool } from '../../api';
import { DataTable, DetailPage, Fields, Section } from '../../components/layout';
import { CodeBlock } from '../../components/layout/CodeBlock';
import { ToolTypeChip } from '../../components/StatusChip';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';
import { formatTime, prettyJson } from '../../lib/format';

export function ToolDetailPage() {
  const { id = '' } = useParams();
  const [query] = useSearchParams();
  const me = useCurrentUser();
  const versions = useResource(`tool-versions:${id}`, () => api.tools.versions(id));
  const latest = versions.data?.[0];
  const versionId = query.get('version');
  const current = versionId ? versions.data?.find((tool) => tool.versionId === versionId) : latest;
  const owned = !!current && !!me?.ownedRoles.includes(current.role);
  return (
    <DetailPage back={{ to: '/tools', label: 'Tools' }}
      title={latest ? `${latest.role}.${latest.name}` : 'Tool'}
      subtitle={current && `${current.service} / ${current.role} · ${current.method} ${current.baseUrl ?? ''}${current.pathTemplate}`}
      status={current && <Chip size="small" label={`v${current.ver}${current.retired ? ' · retired' : ''}`} variant="outlined" />}
      error={versions.error} loading={versions.loading}
      actions={owned && !current.retired && (
        <>
          {current === latest && <Button component={RouterLink} to={`/tools/${id}/edit`} variant="outlined" startIcon={<EditOutlined />}>Edit</Button>}
          <Button component={RouterLink} to={`/tools/${id}/call?version=${current.versionId}`} variant="contained" startIcon={<ApiOutlined />}>Call tool</Button>
        </>
      )}>
      {latest && !current && <Alert severity="error">This version does not belong to the selected tool.</Alert>}
      {current && (
        <Section title="API contract">
          <Stack spacing={2}>
            {current.description && <Typography variant="body2">{current.description}</Typography>}
            <Fields items={[
              ['Provider', `${current.service} / ${current.role}`],
              ['Type', <ToolTypeChip type={current.type} />],
              ['Endpoint', `${current.method} ${current.baseUrl ?? ''}${current.pathTemplate}`],
              ['Gateway definition', current.gatewayApiId],
            ]} />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'repeat(2, minmax(0, 1fr))' }, gap: 2 }}>
              <Stack spacing={0.75}><Typography component="h3" variant="subtitle2">Input schema</Typography><CodeBlock text={prettyJson(current.inputSchemaJson)} /></Stack>
              <Stack spacing={0.75}><Typography component="h3" variant="subtitle2">Output schema</Typography><CodeBlock text={prettyJson(current.outputSchemaJson)} /></Stack>
            </Box>
          </Stack>
        </Section>
      )}
      {latest && <Section title="Version history">
        <DataTable<Tool> rows={versions.data} rowKey={(tool) => tool.versionId} rowLink={(tool) => `/tools/${id}?version=${tool.versionId}`} columns={[
          { header: 'Version', render: (tool) => `v${tool.ver}${tool === latest ? ' · latest' : ''}` },
          { header: 'Endpoint', render: (tool) => `${tool.method} ${tool.pathTemplate}`, mono: true },
          { header: 'Published by', render: (tool) => tool.updatedBy },
          { header: 'Published', render: (tool) => formatTime(tool.updatedAt) },
        ]} />
      </Section>}
    </DetailPage>
  );
}
