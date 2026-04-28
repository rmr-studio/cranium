
# TypeDefinition


## Properties

Name | Type
------------ | -------------
`key` | string
`id` | string
`type` | [EntityTypeRequestDefinition](EntityTypeRequestDefinition.md)
`name` | string
`iconType` | [IconType](IconType.md)
`iconColour` | [IconColour](IconColour.md)
`cardinalityDefault` | [EntityRelationshipCardinality](EntityRelationshipCardinality.md)
`targetRules` | [Array&lt;SaveTargetRuleRequest&gt;](SaveTargetRuleRequest.md)
`semantics` | [SaveSemanticMetadataRequest](SaveSemanticMetadataRequest.md)
`schema` | [SchemaUUID](SchemaUUID.md)

## Example

```typescript
import type { TypeDefinition } from ''

// TODO: Update the object below with actual values
const example = {
  "key": null,
  "id": null,
  "type": null,
  "name": null,
  "iconType": null,
  "iconColour": null,
  "cardinalityDefault": null,
  "targetRules": null,
  "semantics": null,
  "schema": null,
} satisfies TypeDefinition

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TypeDefinition
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


