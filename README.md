# shadow-grove benchmark

This is an implementation of of the [js-framework benchmark](https://github.com/krausest/js-framework-benchmark) using [shadow-grove](https://github.com/thheller/shadow-experiments).

It is not meant to be a competitive benchmark. Results vary from 5 to 40 times slower than vanillajs. Results look terrible but are somewhat expected given that it uses a normalized DB which is queried via EQL. The library code has undergone no tuning at all either.

I'll probably make a lower level variant using only `shadow-arborist` at some point to be more in line with what the other "frameworks" do. A quick glance at some profiles show the majority of time spent is within all the data processing and very little in the actual "rendering". Results should look better when that is optimized.