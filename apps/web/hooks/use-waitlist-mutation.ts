"use client";

import { useMutation } from "@tanstack/react-query";
import posthog from "posthog-js";
import { useRef } from "react";
import { toast } from "sonner";
import type { WaitlistJoinData } from "@/lib/validations";
import { createClient } from "@/lib/supabase";

const PostgresErrorCode = {
  UniqueViolation: "23505",
} as const;

export function useWaitlistJoinMutation() {
  const toastRef = useRef<string | number | undefined>(undefined);

  return useMutation({
    mutationFn: async (data: WaitlistJoinData): Promise<void> => {
      const { error } = await createClient()
        .from("waitlist_submissions")
        .insert({
          name: data.name,
          email: data.email,
        });

      if (error) {
        if (error.code === PostgresErrorCode.UniqueViolation) {
          throw new Error("This email is already on the waitlist!");
        }
        throw new Error(error.message);
      }
    },
    onMutate: () => {
      toastRef.current = toast.loading("Joining waitlist...");
    },
    onSuccess: () => {
      toast.success("You're on the list!", {
        id: toastRef.current,
      });
    },
    onError: (error: Error) => {
      posthog.capture("waitlist_join_failed", {
        error: error.message,
      });
      toast.error(error.message, { id: toastRef.current });
    },
  });
}
