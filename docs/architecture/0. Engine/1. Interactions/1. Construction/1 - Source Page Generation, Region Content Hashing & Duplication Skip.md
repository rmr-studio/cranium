---
tags:
  - wiki-layer-0
  - wiki-ingestion-pass-0
---
## Overview
After data has been ingested from its origin connector, the next steps walking the tree of source material, and determining whether to
- Create new source page and pointer citing a region from the source material
- Update existing source regions to reflect updated in source material
- Skip and move on to avoid burning tokens on unchanged source material
This is all to build an update a provenance graph
### Source
A source file represents **one externally-identifiable** artifact at the smallest URI that can be cited with relevancy and by itself can be used to form useful information 

A source in itself is a pointer to a specific **thing**
- a file
- a notion document
- a slack thread
This holds source references (ie. file path). Overall summarisation of that one particular area and the extension pages that reference this source when generating an overall synthesis for a particular topic or domain.

A source page contains many **regions**.
- a *small meaningful region* of a particular source of information within that file that is hashed
	- *heading section*
	- *function body*
	- *class definition* 
	- *paragraph*
- A region cannot be explained without the context of the source file
	- A singular kotlin function is a region, as you would need context of the class/service that it belongs to
- These regions are then diffed to determine if a file or document has been changed, and if new information must be consumed and updated. 

```
  sources:
    - path: ~/dev/example/auth.kt
      cited_region: "fun handleSession"
      content_hash: <sha256>
```

### Usage
Every page in the third layer of the wiki, is a synthesised claim, summary, and overall insight generated from one to many source files, where pages represent aggregations of data, narrative arcs, trends etc using the information derived from the source material

## Example
- Consumption of a Github Repository
- Consumption of a Slack Channel
- Consumption of a Confluence 

**Tools**
- Bespoke implementation
	- SHA-256 Hashing every time information is ingested/extracted from a source document