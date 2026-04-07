
# ContentNode

Content node containing a block with optional children

## Properties

Name | Type
------------ | -------------
`block` | [Block](Block.md)
`type` | [NodeType](NodeType.md)
`warnings` | Array&lt;string&gt;
`children` | [Array&lt;Node&gt;](Node.md)

## Example

```typescript
import type { ContentNode } from ''

// TODO: Update the object below with actual values
const example = {
  "block": null,
  "type": null,
  "warnings": null,
  "children": null,
} satisfies ContentNode

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ContentNode
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


