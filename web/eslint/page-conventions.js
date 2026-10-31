// Every screen is one of three page kinds, and each kind renders through its layout component, so all screens
// behave the same way (header and actions on top, filters above the list, forms with submit/cancel at the bottom).
// A file in src/pages/ must be named *ListPage.tsx, *DetailPage.tsx or *FormPage.tsx and import the matching
// layout from src/components/layout/.
const KINDS = ['ListPage', 'DetailPage', 'FormPage'];
const EXEMPT = new Set(['NotFoundPage.tsx']);

export default {
  rules: {
    'page-kind': {
      meta: {
        type: 'problem',
        messages: {
          name: 'A page file must be named *ListPage.tsx, *DetailPage.tsx or *FormPage.tsx (see CLAUDE.md "UI conventions").',
          layout: '{{kind}} files must render through the <{{kind}}> layout component from components/layout.',
        },
        schema: [],
      },
      create(context) {
        const file = context.filename.split(/[\\/]/).pop();
        if (EXEMPT.has(file)) return {};
        const kind = KINDS.find((k) => file.endsWith(`${k}.tsx`));
        let imported = false;
        return {
          ImportDeclaration(node) {
            if (kind && node.source.value.includes('components/layout') && node.specifiers.some((s) => s.local.name === kind)) {
              imported = true;
            }
          },
          'Program:exit'(node) {
            if (!kind) context.report({ node, messageId: 'name' });
            else if (!imported) context.report({ node, messageId: 'layout', data: { kind } });
          },
        };
      },
    },
  },
};
