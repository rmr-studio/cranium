
# EntityTypeSemanticMetadata


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`entityTypeId` | string
`targetType` | [SemanticMetadataTargetType](SemanticMetadataTargetType.md)
`targetId` | string
`definition` | string
`classification` | [SemanticAttributeClassification](SemanticAttributeClassification.md)
`signalType` | [MatchSignalType](MatchSignalType.md)
`tags` | Array&lt;string&gt;
`createdAt` | Date
`updatedAt` | Date
`createdBy` | string
`updatedBy` | string

## Example

```typescript
import type { EntityTypeSemanticMetadata } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "entityTypeId": null,
  "targetType": null,
  "targetId": null,
  "definition": null,
  "classification": null,
  "signalType": null,
  "tags": null,
  "createdAt": null,
  "updatedAt": null,
  "createdBy": null,
  "updatedBy": null,
} satisfies EntityTypeSemanticMetadata

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EntityTypeSemanticMetadata
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


