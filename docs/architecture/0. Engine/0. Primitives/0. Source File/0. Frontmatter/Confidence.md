---
tags:
  - wiki-layer-0
  - wiki-primitive
  - wiki-source
  - wiki-source-concept
---
### **Overview**

The confidence of a source plays 6 different roles in the formation, indexing, maintenance and restructuring of the wiki throughout its continuous life-cycle of ingestion and drift maintenance.
Confidence is described by its weight (0-1), and is a signal-quality scalar the engine multiples and weighs up when making decisions about source quality, and how much the derived information should bias the overall outcome of its decisions.
It's usage has an equivalent relationship to how [[Kalman Filter]] uses variance/precision to estimate the value of unknown variables. By adjusting "how much this source's claim moves your posterior"
- At synthesis time -> If two sources disagree or contradict. Which one will come out on top (higher confidence weight) and have the higher standing 
- At retrieval time -> When two pages are equally relevant. What concepts from what page are surfaced and grounded in the overall output


**Wiki Usage**
- [[Synthesis Generation]]
- [[Retrieval Ranking]]
- [[Canonical Promotion Gates]]
- [[Contradiction Resolution and Superceding]]
- [[0.1 - Pass Routing & Eligibility]]
- [[Weighted Drift Priority Queue]]

**Attributes**
- `confidence_score` 