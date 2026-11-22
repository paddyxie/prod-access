import { Alert } from '@mui/material';
import { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { api, ApiError, type ParamDef, type Params } from '../../api';
import { FormPage } from '../../components/layout';
import { ParamsForm } from '../../components/workflow/ParamsForm';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';

export function WorkflowRunFormPage() {
  const { id = '' } = useParams();
  const [query] = useSearchParams();
  const version = query.get('version');
  const navigate = useNavigate();
  const me = useCurrentUser();
  const data = useResource(`workflow-run:${id}:${version}`, async () => {
    const workflow = await api.workflows.get(id);
    const selected = version === null ? workflow.latestVersion : Number(version);
    if (!Number.isSafeInteger(selected) || selected < 1) throw new Error('Invalid workflow version.');
    return { workflow, version: await api.workflows.version(id, selected) };
  });
  const [values, setValues] = useState<Params>({});
  const [errors, setErrors] = useState<Record<string, string>>({});

  const t = data.data?.workflow;
  const bindings = data.data?.version.toolBindings ?? [];
  const roles = [...new Set(bindings.map((tool) => tool.key.split('.')[0]))];
  const notOwned = roles.filter((role) => !me?.ownedRoles.includes(role));
  const retired = bindings.filter((tool) => tool.retired);
  const defs = data.data ? (JSON.parse(data.data.version.paramsJson) as Record<string, ParamDef>) : {};

  const submit = async () => {
    if (!data.data) return;
    setErrors({});
    try {
      const run = await api.runs.submit(id, data.data.version.ver, values);
      navigate(`/runs/${run.id}`);
    } catch (e) {
      if (e instanceof ApiError) setErrors(e.fields);
      throw e;
    }
  };

  return (
    <FormPage
      back={{ to: `/workflows/${id}${version ? `?version=${version}` : ''}`, label: t?.name ?? 'Workflow' }}
      title={`Run ${t?.name ?? ''}`}
      subtitle={data.data && `v${data.data.version.ver} · ${bindings.map((tool) => `${tool.key} v${tool.ver}`).join(', ') || 'no tools'}`}
      submitLabel="Run"
      onSubmit={submit}
      loading={!data.data || !me}
      loadError={data.error}
      submitDisabled={notOwned.length > 0 || retired.length > 0}
    >
      {notOwned.length > 0 && (
        <Alert severity="warning">
          None of your teams owns {notOwned.join(', ')}, so this run will be refused.
        </Alert>
      )}
      {retired.length > 0 && <Alert severity="warning">Retired tool: {retired.map((tool) => tool.key).join(', ')}</Alert>}
      <ParamsForm defs={defs} values={values} onChange={(k, v) => setValues((p) => ({ ...p, [k]: v }))} errors={errors} />
    </FormPage>
  );
}
