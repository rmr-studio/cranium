
# EntityTypeWithSemanticsResponse


## Properties

Name | Type
------------ | -------------
`entityType` | [EntityType](EntityType.md)
`relationships` | [Array&lt;RelationshipDefinition&gt;](RelationshipDefinition.md)
`semantics` | [SemanticMetadataBundle](SemanticMetadataBundle.md)

## Example

```typescript
import type { EntityTypeWithSemanticsResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "entityType": null,
  "relationships": null,
  "semantics": null,
} satisfies EntityTypeWithSemanticsResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EntityTypeWithSemanticsResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


