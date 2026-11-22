import { LinearProgress, Link, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';

import { monoFont } from '../../theme';

export interface Column<T> {
  header: string;
  render: (row: T) => ReactNode;
  width?: number | string;
  mono?: boolean;
}

interface Props<T> {
  columns: Column<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string | number;
  /** Where a click on the row goes. */
  rowLink?: (row: T) => string;
  loading?: boolean;
  emptyText?: string;
}

export function DataTable<T>({ columns, rows, rowKey, rowLink, loading, emptyText = 'No results' }: Props<T>) {
  const navigate = useNavigate();
  return (
    <TableContainer component={Paper} variant="outlined">
      {loading && <LinearProgress />}
      <Table size="small" sx={{ minWidth: rowLink ? 480 : undefined }}>
        <TableHead>
          <TableRow>
            {columns.map((c) => (
              <TableCell key={c.header} sx={{ width: c.width, color: 'text.secondary', bgcolor: 'action.hover', whiteSpace: 'nowrap' }}>
                {c.header}
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows?.map((row) => (
            <TableRow
              key={rowKey(row)}
              hover={!!rowLink}
              onClick={rowLink ? (event) => {
                if (!(event.target instanceof Element) || !event.target.closest('a, button')) navigate(rowLink(row));
              } : undefined}
              sx={rowLink ? { cursor: 'pointer' } : undefined}
            >
              {columns.map((c, index) => (
                <TableCell key={c.header} sx={c.mono ? { fontFamily: monoFont } : undefined}>
                  {index === 0 && rowLink ? <Link component={RouterLink} to={rowLink(row)} underline="hover" sx={{ fontWeight: 650 }}>{c.render(row)}</Link> : c.render(row)}
                </TableCell>
              ))}
            </TableRow>
          ))}
          {rows?.length === 0 && (
            <TableRow>
              <TableCell colSpan={columns.length}>
                <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
                  {emptyText}
                </Typography>
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
