
# SaveTargetRuleRequest

Request to save a target rule for a relationship definition

## Properties

Name | Type
------------ | -------------
`id` | string
`targetEntityTypeId` | string
`cardinalityOverride` | [EntityRelationshipCardinality](EntityRelationshipCardinality.md)
`inverseName` | string

## Example

```typescript
import type { SaveTargetRuleRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "targetEntityTypeId": null,
  "cardinalityOverride": null,
  "inverseName": null,
} satisfies SaveTargetRuleRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SaveTargetRuleRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


