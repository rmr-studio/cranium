
# WorkflowWebhookTriggerConfig

Configuration for WEBHOOK trigger nodes. Triggers workflow execution when an HTTP webhook is received.

## Properties

Name | Type
------------ | -------------
`version` | number
`method` | [RequestMethodType](RequestMethodType.md)
`authentication` | [AuthenticationType](AuthenticationType.md)
`signature` | [Signature](Signature.md)
`payloadSchema` | [SchemaString](SchemaString.md)
`configSchema` | [Array&lt;WorkflowNodeConfigField&gt;](WorkflowNodeConfigField.md)
`config` | { [key: string]: object; }
`subType` | [WorkflowTriggerType](WorkflowTriggerType.md)
`type` | [WorkflowNodeType](WorkflowNodeType.md)

## Example

```typescript
import type { WorkflowWebhookTriggerConfig } from ''

// TODO: Update the object below with actual values
const example = {
  "version": null,
  "method": null,
  "authentication": null,
  "signature": null,
  "payloadSchema": null,
  "configSchema": null,
  "config": null,
  "subType": null,
  "type": null,
} satisfies WorkflowWebhookTriggerConfig

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowWebhookTriggerConfig
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


