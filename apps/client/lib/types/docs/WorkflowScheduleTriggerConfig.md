
# WorkflowScheduleTriggerConfig

Configuration for SCHEDULE trigger nodes. Triggers workflow execution on a schedule.

## Properties

Name | Type
------------ | -------------
`version` | number
`cronExpression` | string
`interval` | [WorkflowScheduleTriggerConfigInterval](WorkflowScheduleTriggerConfigInterval.md)
`timeZone` | [WorkflowScheduleTriggerConfigTimeZone](WorkflowScheduleTriggerConfigTimeZone.md)
`configSchema` | [Array&lt;WorkflowNodeConfigField&gt;](WorkflowNodeConfigField.md)
`config` | { [key: string]: object; }
`subType` | [WorkflowTriggerType](WorkflowTriggerType.md)
`type` | [WorkflowNodeType](WorkflowNodeType.md)

## Example

```typescript
import type { WorkflowScheduleTriggerConfig } from ''

// TODO: Update the object below with actual values
const example = {
  "version": null,
  "cronExpression": null,
  "interval": null,
  "timeZone": null,
  "configSchema": null,
  "config": null,
  "subType": null,
  "type": null,
} satisfies WorkflowScheduleTriggerConfig

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowScheduleTriggerConfig
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


