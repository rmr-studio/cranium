
# SemanticMetadataBundle


## Properties

Name | Type
------------ | -------------
`entityType` | [EntityTypeSemanticMetadata](EntityTypeSemanticMetadata.md)
`attributes` | [{ [key: string]: EntityTypeSemanticMetadata; }](EntityTypeSemanticMetadata.md)
`relationships` | [{ [key: string]: EntityTypeSemanticMetadata; }](EntityTypeSemanticMetadata.md)

## Example

```typescript
import type { SemanticMetadataBundle } from ''

// TODO: Update the object below with actual values
const example = {
  "entityType": null,
  "attributes": null,
  "relationships": null,
} satisfies SemanticMetadataBundle

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SemanticMetadataBundle
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


