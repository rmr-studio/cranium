
# BulkSaveSemanticMetadataRequest


## Properties

Name | Type
------------ | -------------
`targetId` | string
`definition` | string
`classification` | [SemanticAttributeClassification](SemanticAttributeClassification.md)
`tags` | Array&lt;string&gt;

## Example

```typescript
import type { BulkSaveSemanticMetadataRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "targetId": null,
  "definition": null,
  "classification": null,
  "tags": null,
} satisfies BulkSaveSemanticMetadataRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BulkSaveSemanticMetadataRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


