import Editor, { loader } from '@monaco-editor/react';
import { Box } from '@mui/material';
import { useColorScheme } from '@mui/material/styles';
import * as monaco from 'monaco-editor';
import EditorWorker from 'monaco-editor/editor/editor.worker?worker';
import JsonWorker from 'monaco-editor/language/json/json.worker?worker';
import TsWorker from 'monaco-editor/language/typescript/ts.worker?worker';
import { useEffect } from 'react';
import type { Tool } from '../api';
import { sandboxDeclarations } from '../lib/sandboxTypes';

// Monaco is bundled, not fetched from a CDN (the production network cannot reach one).
self.MonacoEnvironment = {
  getWorker: (_id, label) => {
    if (label === 'typescript' || label === 'javascript') return new TsWorker();
    if (label === 'json') return new JsonWorker();
    return new EditorWorker();
  },
};
loader.config({ monaco });

const js = monaco.typescript.javascriptDefaults;
js.setCompilerOptions({
  target: monaco.typescript.ScriptTarget.ESNext,
  module: monaco.typescript.ModuleKind.ESNext,
  lib: ['es2022'], // no DOM: the sandbox has no browser APIs
  allowNonTsExtensions: true,
  checkJs: true,
});
let sandboxLib: monaco.IDisposable | undefined;

interface Props {
  value: string;
  onChange?: (value: string) => void;
  language?: 'javascript' | 'json';
  readOnly?: boolean;
  height?: number | string;
  /** The tool registry, for completion and checking of `api.<role>.<tool>`. */
  tools?: Tool[];
}

export function CodeEditor({ value, onChange, language = 'javascript', readOnly, height = 420, tools }: Props) {
  const { mode, systemMode } = useColorScheme();
  const dark = (mode === 'system' ? systemMode : mode) === 'dark';

  useEffect(() => {
    if (!tools) return;
    sandboxLib?.dispose();
    sandboxLib = js.addExtraLib(sandboxDeclarations(tools), 'file:///prodaccess-sandbox.d.ts');
  }, [tools]);

  return (
    <Box sx={{ border: 1, borderColor: 'divider', borderRadius: 1, overflow: 'hidden' }}>
      <Editor
        height={height}
        language={language}
        value={value}
        onChange={(v) => onChange?.(v ?? '')}
        theme={dark ? 'vs-dark' : 'vs'}
        options={{
          readOnly,
          minimap: { enabled: false },
          fontSize: 13,
          scrollBeyondLastLine: false,
          tabSize: 2,
          automaticLayout: true,
          domReadOnly: readOnly,
        }}
      />
    </Box>
  );
}
