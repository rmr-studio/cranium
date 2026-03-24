export interface BlogPostMeta {
  slug: string;
  title: string;
  description: string;
  date: string;
  updated?: string;
  author: string;
  category: BlogCategory;
  tags: string[];
  coverImage?: string;
  featured?: boolean;
  readTime: number;
}

export type BlogCategory =
  | 'tool-comparison'
  | 'operational-intelligence'
  | 'category-definition'
  | 'changelog';

export const CATEGORY_LABELS: Record<BlogCategory, string> = {
  'tool-comparison': 'Tool Comparisons',
  'operational-intelligence': 'Operational Intelligence',
  'category-definition': 'Category Definitions',
  changelog: 'Changelog',
};

export interface BlogPost extends BlogPostMeta {
  content: string;
  headings: Heading[];
}

export interface Heading {
  text: string;
  slug: string;
  level: 2 | 3;
}
