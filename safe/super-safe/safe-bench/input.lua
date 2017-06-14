-- example HTTP POST script which demonstrates setting the
-- -- HTTP method, body, and adding a header
--
--
--headers_template = "-H 'Host: localhost' -H '{accept}' -H 'Connection: keep-alive'"
--wrk.headers["Content-Type"] = "application/json"
--wrk.body   = "u=1&s=16"

--wrk.method  = "GET"
wrk.method  = "POST"
--wrk.body   = "?Y=worl" -- &baz=quux"
wrk.body   = "?X=world" -- &baz=quux"
wrk.headers["Host"] = "localhost"
wrk.headers["Content-Type"] = "application/x-www-form-urlencoded"
wrk.headers["Connection"] = "keep-alive"

done = function(summary, latency, requests)
   io.write("------------------------------\n")
   for _, p in pairs({1, 10, 25, 50, 75, 90, 95, 99, 99.99 }) do
      n = latency:percentile(p)
      io.write(string.format("\t%5g%%\t%7.2fms\n", p, n/1000))
   end
end
