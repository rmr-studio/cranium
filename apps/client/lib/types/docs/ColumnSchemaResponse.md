
# ColumnSchemaResponse


## Properties

Name | Type
------------ | -------------
`columnName` | string
`pgDataType` | string
`nullable` | boolean
`isPrimaryKey` | boolean
`isForeignKey` | boolean
`fkTarget` | [FkTargetRef](FkTargetRef.md)
`existingMapping` | [ExistingMappingRef](ExistingMappingRef.md)
`suggestedSchemaType` | [SchemaType](SchemaType.md)
`autoDetectedCursor` | boolean

## Example

```typescript
import type { ColumnSchemaResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "columnName": null,
  "pgDataType": null,
  "nullable": null,
  "isPrimaryKey": null,
  "isForeignKey": null,
  "fkTarget": null,
  "existingMapping": null,
  "suggestedSchemaType": null,
  "autoDetectedCursor": null,
} satisfies ColumnSchemaResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ColumnSchemaResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


