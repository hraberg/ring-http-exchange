# ring-http-exchange

Clojure [ring](https://github.com/ring-clojure/ring) adapter for
[`com.sun.net.httpserver.HttpServer`](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpServer.html)
which is included in the JDK.

The main motivation for this is to support starting a small HTTP
server inside an application which itself isn't necessary primarily a
web app, while avoiding adding any new dependencies on the classpath
(apart from ring-core). It could also be used for tests.

HTTP only for now. Untested.

## Usage

``` clojure
(run-http-server
  (fn [request]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body "Hello World"}
  {:port 8080})
```

The options are a subset of the ones support by
[ring-jetty-adapter](https://github.com/ring-clojure/ring/tree/master/ring-jetty-adapter). See
docstring for details.

## License

Copyright © 2017 Håkan Råberg

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
