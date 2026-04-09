
# CompleteOnboardingResponse


## Properties

Name | Type
------------ | -------------
`workspace` | [Workspace](Workspace.md)
`user` | [UserDisplay](UserDisplay.md)
`templateResult` | [TemplateInstallResult](TemplateInstallResult.md)
`inviteResults` | [Array&lt;InviteResult&gt;](InviteResult.md)
`definitionResults` | [Array&lt;BusinessDefinitionResult&gt;](BusinessDefinitionResult.md)

## Example

```typescript
import type { CompleteOnboardingResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "workspace": null,
  "user": null,
  "templateResult": null,
  "inviteResults": null,
  "definitionResults": null,
} satisfies CompleteOnboardingResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CompleteOnboardingResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


