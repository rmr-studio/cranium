
# BlockComponentNode


## Properties

Name | Type
------------ | -------------
`id` | string
`type` | [ComponentType](ComponentType.md)
`props` | object
`bindings` | [Array&lt;BlockBinding&gt;](BlockBinding.md)
`slots` | { [key: string]: Array&lt;string&gt;; }
`slotLayout` | [{ [key: string]: LayoutGrid; }](LayoutGrid.md)
`widgetMeta` | { [key: string]: object; }
`visible` | [Condition](Condition.md)
`fetchPolicy` | [BlockFetchPolicy](BlockFetchPolicy.md)

## Example

```typescript
import type { BlockComponentNode } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "type": null,
  "props": null,
  "bindings": null,
  "slots": null,
  "slotLayout": null,
  "widgetMeta": null,
  "visible": null,
  "fetchPolicy": null,
} satisfies BlockComponentNode

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BlockComponentNode
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


