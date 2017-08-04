# ring-http-exchange

Clojure [ring](https://github.com/ring-clojure/ring) adapter for
[`com.sun.net.httpserver.HttpServer`](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpServer.html).

HTTP only for now.

## Usage

``` clojure
(run-http-server
  (fn [req]
    {:body "Hello World"
     :headers {"content-type" "text/plain"}
     :status 200})
  {:port 8080})
```

## License

Copyright © 2017 Håkan Råberg

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
