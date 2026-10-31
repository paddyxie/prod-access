import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import pages from './eslint/page-conventions.js';

// Building blocks that only components/ may use directly; pages compose the layout components instead.
const LAYOUT_ONLY = ['Table', 'TableBody', 'TableCell', 'TableContainer', 'TableHead', 'TableRow', 'TablePagination', 'Drawer', 'AppBar'];

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'eslint'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: { globals: globals.browser },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'no-restricted-syntax': [
        'error',
        { selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']", message: 'Render data as text; never inject HTML.' },
        { selector: "MemberExpression[object.name='document'][property.name=/^(createElement|querySelector)/]", message: 'Use React, not DOM manipulation.' },
        { selector: "MemberExpression[property.name=/^(innerHTML|outerHTML)$/]", message: 'Render data as text; never inject HTML.' },
      ],
      'no-restricted-properties': [
        'error',
        { object: 'window', property: 'location', message: 'Navigate with the router (useNavigate / <Link>) so the URL and history stay right.' },
      ],
    },
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/api/**'],
    rules: {
      'no-restricted-globals': ['error', { name: 'fetch', message: 'Call the backend through src/api.' }],
    },
  },
  {
    files: ['src/pages/**/*.tsx'],
    plugins: { pages },
    rules: {
      'pages/page-kind': 'error',
      'no-restricted-imports': [
        'error',
        {
          paths: [{ name: '@mui/material', importNames: LAYOUT_ONLY, message: 'Pages use ListPage/DataTable/DetailPage/FormPage from components/layout.' }],
          patterns: [{ group: ['@mui/material/Table*', '@mui/material/Drawer', '@mui/material/AppBar'], message: 'Pages use the layout components.' }],
        },
      ],
    },
  },
);
