// Stub — will be replaced by Task 2's full implementation
// Provides minimal types and functions so sitemap.ts compiles

export interface BlogPost {
  slug: string;
  title: string;
  date: string;
  updated?: string;
  description: string;
  category: string;
  content: string;
}

export interface Category {
  name: string;
  slug: string;
  count: number;
}

export async function getAllPosts(): Promise<BlogPost[]> {
  return [];
}

export async function getCategories(): Promise<Category[]> {
  return [];
}
