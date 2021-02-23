# shadow-grove benchmark

This is an implementation of of the [js-framework benchmark](https://github.com/krausest/js-framework-benchmark) using [shadow-grove](https://github.com/thheller/shadow-experiments).

It is not meant to be a competitive benchmark. Results vary from 5 to 40 times slower than vanillajs. Results look terrible but are somewhat expected given that it uses a normalized DB which is queried via EQL. The library code has undergone no tuning at all either.

I'll probably make a lower level variant using only `shadow-arborist` at some point to be more in line with what the other "frameworks" do. A quick glance at some profiles show the majority of time spent is within all the data processing and very little in the actual "rendering". Results should look better when that is optimized.

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