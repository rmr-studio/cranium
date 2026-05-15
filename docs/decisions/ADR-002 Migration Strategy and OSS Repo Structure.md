**Context**
This repository is derived from a previous project `Riven` that while had a similar concept, is built up of architecture and systems that are no longer relevant, and the core data structure `entities` does not fit the core structure of this project `pages`. However the supporting implementations (Observations, RBAC etc ) will be ported over.

The main strategy will be a fresh springboot project, copying over all relevant source material, and continouslyl extracting relevant business logic. But too much effort will be required to perform bespoke reworks, when the entire thing is no longer relevant

Another relevant decision is that individual sub modules that can act as their own useful libraries
- Github Walker and Extractor
- Notion Extraction
Will be moved into their own repositories and published to Gradle. This is to further support the OSS distribution strategy, and just provide more value back to the community with useful building blocks