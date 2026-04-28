
# ExistingMappingRef


## Properties

Name | Type
------------ | -------------
`attributeName` | string
`schemaType` | [SchemaType](SchemaType.md)
`isIdentifier` | boolean
`isSyncCursor` | boolean
`isMapped` | boolean

## Example

```typescript
import type { ExistingMappingRef } from ''

// TODO: Update the object below with actual values
const example = {
  "attributeName": null,
  "schemaType": null,
  "isIdentifier": null,
  "isSyncCursor": null,
  "isMapped": null,
} satisfies ExistingMappingRef

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ExistingMappingRef
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


