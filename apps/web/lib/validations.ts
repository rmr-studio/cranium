import { z } from "zod";

// Phase 1: join
export const waitlistJoinSchema = z.object({
  name: z.string().min(1, "Name is required"),
  email: z
    .string()
    .min(1, "Email is required")
    .email("Please enter a valid email address"),
});
export type WaitlistJoinData = z.infer<typeof waitlistJoinSchema>;

// Phase 2: survey
export const waitlistSurveySchema = z.object({
  businessOverview: z.string().optional(),
  painPoints: z
    .array(z.string())
    .min(1, "Please select at least one pain point")
    .max(3, "Please select at most 3"),
  painPointsOther: z.string().optional(),
  integrations: z
    .array(z.string())
    .min(1, "Please select at least one integration")
    .max(5, "Please select at most 5 integrations"),
  involvement: z.enum(["WAITLIST", "EARLY_TESTING", "CALL_EARLY_TESTING"], {
    required_error: "Please select an option",
  }),
});
export type WaitlistSurveyData = z.infer<typeof waitlistSurveySchema>;

// Combined (used by useForm)
export const waitlistFormSchema = waitlistJoinSchema.merge(waitlistSurveySchema);
export type WaitlistMultiStepFormData = z.infer<typeof waitlistFormSchema>;
