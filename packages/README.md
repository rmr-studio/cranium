# Packages

Shared packages used across the monorepo.

| Package | What it is |
|---------|------------|
| [`ui`](ui/) | React component library (shadcn/ui-based) |
| [`hooks`](hooks/) | React hooks |
| [`utils`](utils/) | Utility functions |
| [`tsconfig`](tsconfig/) | TypeScript configurations |

## Usage

```tsx
import { Button } from "@riven/ui";
import { useDebounce } from "@riven/hooks";
import { cn } from "@riven/utils";
```
