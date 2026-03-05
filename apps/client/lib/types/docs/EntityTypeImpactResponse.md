
# EntityTypeImpactResponse


## Properties

Name | Type
------------ | -------------
`error` | string
`updatedEntityTypes` | [{ [key: string]: EntityType; }](EntityType.md)
`impact` | [DeleteDefinitionImpact](DeleteDefinitionImpact.md)

## Example

```typescript
import type { EntityTypeImpactResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "error": null,
  "updatedEntityTypes": null,
  "impact": null,
} satisfies EntityTypeImpactResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EntityTypeImpactResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


