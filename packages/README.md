# Packages

Shared packages used across the monorepo.

| Package                 | What it is                                |
| ----------------------- | ----------------------------------------- |
| [`ui`](ui/)             | React component library (shadcn/ui-based) |
| [`hooks`](hooks/)       | React hooks                               |
| [`utils`](utils/)       | Utility functions                         |
| [`tsconfig`](tsconfig/) | TypeScript configurations                 |

## Usage

```tsx
import { Button } from "@cranium/ui";
import { useDebounce } from "@cranium/hooks";
import { cn } from "@cranium/utils";
```
