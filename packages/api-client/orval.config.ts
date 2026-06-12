import { defineConfig } from 'orval';

// openapi.json is a committed snapshot of the backend's /v3/api-docs;
// refresh it (and the generated code) with scripts/generate-api-client.sh.
export default defineConfig({
  rota: {
    input: './openapi.json',
    output: {
      target: './src/generated/rota.ts',
      schemas: './src/generated/model',
      client: 'react-query',
      httpClient: 'fetch',
      clean: true,
      prettier: false,
      override: {
        mutator: {
          path: './src/mutator.ts',
          name: 'customFetch',
        },
        fetch: {
          // Hooks resolve to the response body directly; non-2xx throws ApiError.
          includeHttpResponseReturnType: false,
        },
      },
    },
  },
});
