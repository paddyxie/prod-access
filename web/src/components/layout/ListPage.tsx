import { Alert, Stack } from '@mui/material';
import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router';
import { useResource } from '../../hooks/useResource';
import { DataTable, type Column } from './DataTable';
import { FilterBar, type FilterDef } from './FilterBar';
import { PageHeader } from './PageHeader';
import { PageBody } from './PageBody';

interface Props<T> {
  title: string;
  subtitle?: ReactNode;
  /** Page-level buttons such as "New ...", top right. */
  actions?: ReactNode;
  /** Query conditions, rendered above the table and kept in the URL query string. */
  filters: FilterDef[];
  load: (filters: Record<string, string>) => Promise<T[]>;
  columns: Column<T>[];
  rowKey: (row: T) => string;
  rowLink?: (row: T) => string;
  emptyText?: string;
  pollMs?: number;
}

/** The one layout for every list screen: header, then filters, then the table. */
export function ListPage<T>({ title, subtitle, actions, filters, load, columns, rowKey, rowLink, emptyText, pollMs }: Props<T>) {
  const [params, setParams] = useSearchParams();
  const values = Object.fromEntries(filters.map((f) => [f.name, params.get(f.name) ?? '']));
  const { data, error, loading } = useResource(`list:${title}:${JSON.stringify(values)}`, () => load(values), pollMs);

  const setFilter = (name: string, value: string) =>
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (value) next.set(name, value);
        else next.delete(name);
        return next;
      },
      { replace: true },
    );

  return (
    <>
      <PageHeader title={title} subtitle={subtitle} actions={actions} />
      <PageBody>
        <Stack spacing={1.5}>
          {filters.length > 0 && <FilterBar defs={filters} values={values} onChange={setFilter} onReset={() => setParams({}, { replace: true })} />}
          {error && <Alert severity="error">{error.message}</Alert>}
          <DataTable columns={columns} rows={data} rowKey={rowKey} rowLink={rowLink} loading={loading} emptyText={emptyText} />
        </Stack>
      </PageBody>
    </>
  );
}
