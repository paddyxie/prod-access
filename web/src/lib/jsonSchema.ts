/** Contract types for editor completion. Unknown schemas become unknown, never any. */
export type JsonSchema = boolean | {
  type?: string | string[];
  properties?: Record<string, JsonSchema>;
  required?: string[];
  additionalProperties?: JsonSchema;
  items?: JsonSchema;
  enum?: unknown[];
  const?: unknown;
};
export function schemaType(schema: JsonSchema, depth = 0): string {
  if (depth > 40 || schema === true) return 'unknown';
  if (schema === false) return 'never';
  if (schema.const !== undefined) return JSON.stringify(schema.const);
  if (schema.enum) return schema.enum.map((v) => JSON.stringify(v)).join(' | ');
  const types = Array.isArray(schema.type) ? schema.type : [schema.type ?? (schema.properties ? 'object' : '')];
  return types.map((type) => {
    switch (type) {
      case 'object': {
        const props = Object.entries(schema.properties ?? {}).map(([name, value]) =>
          `${JSON.stringify(name)}${schema.required?.includes(name) ? '' : '?'}: ${schemaType(value, depth + 1)}`,
        );
        if (schema.additionalProperties !== false) props.push('[key: string]: unknown');
        return `{ ${props.join('; ')} }`;
      }
      case 'array': return `Array<${schemaType(schema.items ?? true, depth + 1)}>`;
      case 'integer': case 'number': return 'number';
      case 'string': return 'string';
      case 'boolean': return 'boolean';
      case 'null': return 'null';
      default: return 'unknown';
    }
  }).join(' | ');
}
