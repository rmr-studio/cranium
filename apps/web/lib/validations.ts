import { z } from "zod";

export const waitlistJoinSchema = z.object({
  name: z.string().min(1, "Name is required"),
  email: z
    .string()
    .min(1, "Email is required")
    .email("Please enter a valid email address"),
});
export type WaitlistJoinData = z.infer<typeof waitlistJoinSchema>;
