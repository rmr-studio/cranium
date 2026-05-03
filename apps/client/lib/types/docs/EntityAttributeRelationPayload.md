
# EntityAttributeRelationPayload

An attribute payload representing a relationship to another entity, with a full identifying link

## Properties

Name | Type
------------ | -------------
`type` | [EntityPropertyType](EntityPropertyType.md)
`value` | object
`schemaType` | [SchemaType](SchemaType.md)
`relations` | [Array&lt;EntityLink&gt;](EntityLink.md)

## Example

```typescript
import type { EntityAttributeRelationPayload } from ''

// TODO: Update the object below with actual values
const example = {
  "type": null,
  "value": null,
  "schemaType": null,
  "relations": null,
} satisfies EntityAttributeRelationPayload

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EntityAttributeRelationPayload
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


