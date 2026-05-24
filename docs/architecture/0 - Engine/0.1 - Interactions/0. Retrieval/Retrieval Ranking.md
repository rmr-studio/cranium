### Confidence
When deriving outputs from pages and sources, the overall final ranking score to determine confidence in derived information can be calculated by all the referenced sources 
	  - `relevance * confidence`
  - It can also be used to pull the most relevant page and output content
	  - if two pages match equally on semantics, the tie-break will be based on overall confidence of cited sources in relation the outputted information

  ```
  A user asks: "Why did we adopt deterministic-attribute-uuid-mapping?"

  Engine pulls four candidate sources:
  - ADR-007 in ~/dev/cranium/docs/decisions/ (weight 0.8)
  - The Kotlin commit that implemented it (weight 1.0)
  - A Slack thread debating it pre-decision (weight 0.05)
  - A Notion meeting note summarizing the discussion (weight 0.4)

  L3 synthesis behavior:
  - Code (1.0) + ADR (0.8) anchor the "Choice" and "Why."
  - Notion (0.4) is read but only quoted if it adds detail the others lack.
  - Slack (0.05) is read for context (what alternatives were considered), and is used to further support and add weighting/evidence back to who was involvled in making the decision (so a user knows who to go to for further discussion) but never used as the basis for a primary claim. If it contradicts the ADR, the ADR wins.**```
  ```
  