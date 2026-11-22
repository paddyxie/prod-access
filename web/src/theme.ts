import { createTheme } from '@mui/material/styles';

export const consoleTheme = createTheme({
  cssVariables: { colorSchemeSelector: 'class' },
  colorSchemes: {
    light: {
      palette: {
        primary: { main: '#5145b8', contrastText: '#fff' },
        secondary: { main: '#475569' },
        background: { default: '#f1f4f9', paper: '#fff' },
        text: { primary: '#162033', secondary: '#48576d' },
        divider: '#d9e0eb',
      },
    },
    dark: {
      palette: {
        primary: { main: '#b3adff', contrastText: '#171439' },
        secondary: { main: '#acb8cb' },
        background: { default: '#101725', paper: '#192335' },
        text: { primary: '#edf2fc', secondary: '#aab8cd' },
        divider: '#344159',
      },
    },
  },
  shape: { borderRadius: 8 },
  typography: {
    fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, sans-serif',
    fontSize: 13,
    h5: { fontSize: '1.12rem', fontWeight: 680, lineHeight: 1.35 },
    subtitle1: { fontSize: '0.9rem', fontWeight: 650 },
    button: { textTransform: 'none', fontWeight: 650 },
  },
  components: {
    MuiButton: { defaultProps: { disableElevation: true, size: 'small' }, styleOverrides: { root: { minHeight: 32 } } },
    MuiTextField: { defaultProps: { size: 'small' } },
    MuiAccordion: { defaultProps: { disableGutters: true, elevation: 0 } },
    MuiChip: { styleOverrides: { sizeSmall: { height: 22, fontWeight: 600 } } },
    MuiTableCell: {
      styleOverrides: {
        head: { fontSize: '0.72rem', fontWeight: 700, letterSpacing: '0.035em' },
        sizeSmall: { padding: '9px 14px' },
      },
    },
  },
});

export const consoleHeaderHeight = 56;
export const monoFont = 'ui-monospace, SFMono-Regular, Consolas, monospace';
