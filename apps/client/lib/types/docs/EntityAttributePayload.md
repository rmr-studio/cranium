
# EntityAttributePayload


## Properties

Name | Type
------------ | -------------
`type` | [EntityPropertyType](EntityPropertyType.md)
`relations` | [Array&lt;EntityLink&gt;](EntityLink.md)
`value` | object
`schemaType` | [SchemaType](SchemaType.md)

## Example

```typescript
import type { EntityAttributePayload } from ''

// TODO: Update the object below with actual values
const example = {
  "type": null,
  "relations": null,
  "value": null,
  "schemaType": null,
} satisfies EntityAttributePayload

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EntityAttributePayload
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


