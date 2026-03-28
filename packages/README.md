# Packages

Shared packages used across the Riven monorepo.

| Package | Description |
|---------|-------------|
| [`ui`](ui/) | Shared React component library (shadcn/ui-based) |
| [`hooks`](hooks/) | Reusable React hooks |
| [`utils`](utils/) | Shared utility functions |
| [`tsconfig`](tsconfig/) | Shared TypeScript configurations |

## Usage

Import from packages using the `@riven/` scope:

```tsx
import { Button } from "@riven/ui";
import { useDebounce } from "@riven/hooks";
import { cn } from "@riven/utils";
```
