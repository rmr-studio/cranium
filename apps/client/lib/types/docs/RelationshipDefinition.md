
# RelationshipDefinition


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`sourceEntityTypeId` | string
`name` | string
`icon` | [Icon](Icon.md)
`cardinalityDefault` | [EntityRelationshipCardinality](EntityRelationshipCardinality.md)
`_protected` | boolean
`systemType` | [SystemRelationshipType](SystemRelationshipType.md)
`targetRules` | [Array&lt;RelationshipTargetRule&gt;](RelationshipTargetRule.md)
`createdAt` | Date
`updatedAt` | Date
`createdBy` | string
`updatedBy` | string
`isPolymorphic` | boolean

## Example

```typescript
import type { RelationshipDefinition } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "sourceEntityTypeId": null,
  "name": null,
  "icon": null,
  "cardinalityDefault": null,
  "_protected": null,
  "systemType": null,
  "targetRules": null,
  "createdAt": null,
  "updatedAt": null,
  "createdBy": null,
  "updatedBy": null,
  "isPolymorphic": null,
} satisfies RelationshipDefinition

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RelationshipDefinition
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


