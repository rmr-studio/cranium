
# RelationshipTargetRule


## Properties

Name | Type
------------ | -------------
`id` | string
`relationshipDefinitionId` | string
`targetEntityTypeId` | string
`semanticTypeConstraint` | [SemanticGroup](SemanticGroup.md)
`cardinalityOverride` | [EntityRelationshipCardinality](EntityRelationshipCardinality.md)
`inverseVisible` | boolean
`inverseName` | string
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { RelationshipTargetRule } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "relationshipDefinitionId": null,
  "targetEntityTypeId": null,
  "semanticTypeConstraint": null,
  "cardinalityOverride": null,
  "inverseVisible": null,
  "inverseName": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies RelationshipTargetRule

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RelationshipTargetRule
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


