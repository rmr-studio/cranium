In a wide-scale LLM wiki system. The sheer amount of generated source and atomic synthesis pages will be extremely large (hundreds of thousands). This means that the system running drift detection and linting will be constantly running through jobs. 

Given the sheer scale of pages that will constantly need to be kept up to date. The system needs to prioritise the most relevant and crucial pages that contain the most confidence and system relevant information that holds the most backing to the majority of queries and operations performed by the system.

### Confidence

In order to ensure that the most relevant sources and pages are not stale. The drift queue is sorted by 
- `Weight (Confidence) * Downstream Page Count`
This means that a 
- Code file change (extremely high confidence)
- That is referenced by 12 pages
Will be picked up and consumed before a
- Confluence Page (mid tier confidence)
- That is referenced by 2 pages


