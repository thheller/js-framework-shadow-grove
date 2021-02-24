# shadow-grove benchmark

This is an implementation of of the [js-framework benchmark](https://github.com/krausest/js-framework-benchmark) using [shadow-grove](https://github.com/thheller/shadow-experiments).

Lots of optimizations left to be done to make this competitive. Looks decent already.

## 2021-02-24

Added a `light` variant that doesn't use any normalized DB or EQL queries. Faster in some aspects actually slower in others. Suffers greatly from `render-seq` behavior being bad. `full` variant actually surprisingly good.

### light
![Screenshot](2021-02-24--14-11.png)

### full
![Screenshot](2021-02-23--10-48.png)


- create (many) rows: expected to be slow because of setting up and running the EQL to get the data
- replace all rows: I guess the warmup makes all the difference here. no clue how it could be faster than create otherwise
- partial update: decent, slow because of EQL queries 
- select row: fast since computation is done in event handler instead of dynamic EQL attribute
- swap rows: only has to update a vector via assoc (fast) and then swap two DOM nodes (also fast)
- remove row: `render-seq` behavior terrible for removals
- append: expected this to be slower but I guess it had enough warmup time when created the first 10,000.
- clear: most time spent in clearing up the normalized DB and EQL query machinery. actual DOM time is a tiny fraction.

## Older versions


This version had terrible `select row` performance because of a EQL computed attribute. Instead now calculating it once in the `::select!` event.

![Screenshot](2021-02-22--12-15.png)