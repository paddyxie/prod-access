import { Box } from '@mui/material';
import { monoFont } from '../../theme';

export function CodeBlock({ text }: { text: string }) {
  return (
    <Box component="pre" sx={{ m: 0, p: 1.25, border: 1, borderColor: 'divider', bgcolor: 'background.default', borderRadius: 1, fontFamily: monoFont, fontSize: '0.75rem', maxHeight: 360, overflow: 'auto', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
      {text || '—'}
    </Box>
  );
}
