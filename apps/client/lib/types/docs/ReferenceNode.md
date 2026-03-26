
# ReferenceNode

Reference node containing a block with entity or block tree references

## Properties

Name | Type
------------ | -------------
`block` | [Block](Block.md)
`warnings` | Array&lt;string&gt;
`type` | [NodeType](NodeType.md)
`reference` | [ReferenceNodeAllOfReference1](ReferenceNodeAllOfReference1.md)

## Example

```typescript
import type { ReferenceNode } from ''

// TODO: Update the object below with actual values
const example = {
  "block": null,
  "warnings": null,
  "type": null,
  "reference": null,
} satisfies ReferenceNode

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ReferenceNode
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


