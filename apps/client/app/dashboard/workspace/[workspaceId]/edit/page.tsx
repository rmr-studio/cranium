import { redirect } from 'next/navigation';

export default async function EditWorkspacePage({
  params,
}: {
  params: Promise<{ workspaceId: string }>;
}) {
  const { workspaceId } = await params;
  redirect(`/dashboard/workspace/${workspaceId}/settings`);
}
