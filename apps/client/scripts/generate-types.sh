#!/bin/bash

TYPES_DIR="lib/types"

# Clean auto-generated directories (preserves manual domain barrels)
rm -rf "$TYPES_DIR/models"
rm -rf "$TYPES_DIR/apis"
rm -rf "$TYPES_DIR/docs"

# Clean auto-generated root files (Angular boilerplate + generator utils)
rm -f "$TYPES_DIR/api.base.service.ts"
rm -f "$TYPES_DIR/api.module.ts"
rm -f "$TYPES_DIR/configuration.ts"
rm -f "$TYPES_DIR/encoder.ts"
rm -f "$TYPES_DIR/git_push.sh"
rm -f "$TYPES_DIR/index.ts"
rm -f "$TYPES_DIR/param.ts"
rm -f "$TYPES_DIR/provide-api.ts"
rm -f "$TYPES_DIR/query.params.ts"
rm -f "$TYPES_DIR/runtime.ts"
rm -f "$TYPES_DIR/variables.ts"
rm -f "$TYPES_DIR/README.md"

npx openapi-generator-cli generate \
      -i http://localhost:8081/docs/v3/api-docs \
      -g typescript-fetch \
      -o lib/types \
      --additional-properties=\
withSeparateModelsAndApi=true,\
modelPackage=models,\
apiPackage=api,\
supportsES6=true,\
stringEnums=true
