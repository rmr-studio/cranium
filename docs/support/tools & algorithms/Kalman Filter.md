---
tags:
  - wiki-support-concept
---

### `Source Material`
[Wikipedia](https://en.wikipedia.org/wiki/Kalman_filter)
### `Overview`
Kalman filtering is an algorithm that is used to provide an accurate estimation of variables and values that are currently unknown to the user by using a series of temporal measurements, such as:
- Statistical Noise
- Other Inaccuracies


It is derived by the concepts of  `z − Hx̂` -> "measurement - prediction"
	- where it is not (predicted)
	- where it is (observed)
The estimation the moves in proportion to the gap, weighted by which side is producing more trustworthy measurements

It can be described by the self tracking missile, knowing where it is by knowing where it isn't
	- real self tracking missiles heavily utilise **Kalman Filters** and other algorithms derived from this

