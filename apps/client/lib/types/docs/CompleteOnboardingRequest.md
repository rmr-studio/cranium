
# CompleteOnboardingRequest


## Properties

Name | Type
------------ | -------------
`workspace` | [OnboardingWorkspace](OnboardingWorkspace.md)
`profile` | [OnboardingProfile](OnboardingProfile.md)
`invites` | [Array&lt;OnboardingInvite&gt;](OnboardingInvite.md)
`businessDefinitions` | [Array&lt;OnboardingBusinessDefinition&gt;](OnboardingBusinessDefinition.md)
`acquisitionChannels` | [Array&lt;AcquisitionChannel&gt;](AcquisitionChannel.md)

## Example

```typescript
import type { CompleteOnboardingRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "workspace": null,
  "profile": null,
  "invites": null,
  "businessDefinitions": null,
  "acquisitionChannels": null,
} satisfies CompleteOnboardingRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CompleteOnboardingRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


