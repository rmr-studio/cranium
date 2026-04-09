
# Node


## Properties

Name | Type
------------ | -------------
`block` | [Block](Block.md)
`type` | [NodeType](NodeType.md)
`warnings` | Array&lt;string&gt;

## Example

```typescript
import type { Node } from ''

// TODO: Update the object below with actual values
const example = {
  "block": null,
  "type": null,
  "warnings": null,
} satisfies Node

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Node
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


