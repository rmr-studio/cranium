
# TableSchemaResponse


## Properties

Name | Type
------------ | -------------
`tableName` | string
`schemaHash` | string
`driftStatus` | [DriftStatus](DriftStatus.md)
`columns` | [Array&lt;ColumnSchemaResponse&gt;](ColumnSchemaResponse.md)
`cursorIndexWarning` | [CursorIndexWarning](CursorIndexWarning.md)
`detectedCursorColumn` | string
`primaryKeyColumn` | string
`existingEntityTypeId` | string

## Example

```typescript
import type { TableSchemaResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "tableName": null,
  "schemaHash": null,
  "driftStatus": null,
  "columns": null,
  "cursorIndexWarning": null,
  "detectedCursorColumn": null,
  "primaryKeyColumn": null,
  "existingEntityTypeId": null,
} satisfies TableSchemaResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TableSchemaResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


