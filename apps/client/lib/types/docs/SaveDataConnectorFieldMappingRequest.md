
# SaveDataConnectorFieldMappingRequest


## Properties

Name | Type
------------ | -------------
`columnName` | string
`attributeName` | string
`schemaType` | [SchemaType](SchemaType.md)
`isIdentifier` | boolean
`isSyncCursor` | boolean
`isMapped` | boolean

## Example

```typescript
import type { SaveDataConnectorFieldMappingRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "columnName": null,
  "attributeName": null,
  "schemaType": null,
  "isIdentifier": null,
  "isSyncCursor": null,
  "isMapped": null,
} satisfies SaveDataConnectorFieldMappingRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SaveDataConnectorFieldMappingRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


