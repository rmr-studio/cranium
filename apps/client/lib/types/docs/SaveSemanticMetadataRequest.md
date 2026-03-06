
# SaveSemanticMetadataRequest


## Properties

Name | Type
------------ | -------------
`definition` | string
`classification` | [SemanticAttributeClassification](SemanticAttributeClassification.md)
`tags` | Array&lt;string&gt;

## Example

```typescript
import type { SaveSemanticMetadataRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "definition": null,
  "classification": null,
  "tags": null,
} satisfies SaveSemanticMetadataRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SaveSemanticMetadataRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


