(ns ring-http-exchange.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ring.core.protocols :as protocols])
  (:import [com.sun.net.httpserver HttpServer HttpHandler HttpExchange]
           [java.io ByteArrayOutputStream File PrintWriter]
           [java.util.concurrent ArrayBlockingQueue ThreadPoolExecutor TimeUnit]
           [java.net InetSocketAddress]))

(set! *warn-on-reflection* true)

(defn- http-exchange->request-map [^HttpExchange exchange]
  {:server-port        (.getPort (.getLocalAddress exchange))
   :server-name        (.getHostName (.getLocalAddress exchange))
   :remote-addr        (.getHostString (.getRemoteAddress exchange))
   :uri                (.getPath (.getRequestURI exchange))
   :query-string       (.getQuery (.getRequestURI exchange))
   :scheme             :http
   :request-method     (keyword (str/lower-case (.getRequestMethod exchange)))
   :protocol           (.getProtocol exchange)
   :headers            (->> (for [[k vs] (.getRequestHeaders exchange)]
                              [(str/lower-case k) (str/join "," vs)])
                            (into {}))
   :ssl-client-cert    nil
   :body               (.getRequestBody exchange)})

(defn- set-response-headers [^HttpExchange exchange headers]
  (doseq [:let [response-headers (.getResponseHeaders exchange)]
          [k v] headers
          v (cond-> v
              (string? v) vector)]
    (.add response-headers (name k) v)))

(def ^{:private true} error-page-template
  (str "<html><head><title>%s</title></head>"
       "<body><h1>%s</h1><pre>%s</pre></body></html>"))

(defn- handle-exception [^HttpExchange exchange ^Throwable t]
  (let [title "Internal Server Error"
        trace (with-out-str
                (.printStackTrace t (PrintWriter. *out*)))
        page (-> (format error-page-template title title trace)
                 (.getBytes "UTF-8"))]
    (.set (.getResponseHeaders exchange) "content-type" "text/html")
    (.sendResponseHeaders exchange 500 (alength page))
    (io/copy page (.getResponseBody exchange))))

(defn- handle-http-exchange [^HttpExchange exchange handler
                             {:keys [output-buffer-size]
                              :or {output-buffer-size 32768}
                              :as options}]
  (with-open [exchange exchange]
    (try
      (let [{:keys [status body headers]
             :as response} (-> exchange http-exchange->request-map handler)
            out (.getResponseBody exchange)]
        (set-response-headers exchange headers)
        (cond
          (= "chunked" (get headers "transfer-encoding"))
          (do (.sendResponseHeaders exchange status 0)
              (protocols/write-body-to-stream body response out))

          (string? body)
          (let [bytes (.getBytes ^String body "UTF-8")]
            (.sendResponseHeaders exchange status (alength bytes))
            (io/copy bytes out))

          (instance? File body)
          (do (.sendResponseHeaders exchange status (.length ^File body))
              (protocols/write-body-to-stream body response out))

          :else
          (let [baos (ByteArrayOutputStream. output-buffer-size)]
            (protocols/write-body-to-stream body response baos)
            (.sendResponseHeaders exchange status (.size baos))
            (io/copy (.toByteArray baos) out))))
      (catch Throwable t
        (.printStackTrace t)
        (handle-exception exchange t))
      (finally
        (.flush (.getResponseBody exchange))))))

(defn ^HttpServer stop-http-server
  "Stops a com.sun.net.httpserver.HttpServer with an optional
  delay (in seconds) to allow active request to finish."
  ([^HttpServer server]
   (stop-http-server server 0))
  ([^HttpServer server delay]
   (doto server
     (.stop delay))))

(defn ^HttpServer  run-http-server
  "Start a com.sun.net.httpserver.HttpServer to serve the given
  handler according to the supplied options:

  :port                 - the port to listen on (defaults to 8080)
  :host                 - the hostname to listen on (defaults to 127.0.0.1)
  :max-threads          - the maximum number of threads to use (default 50)
  :min-threads          - the minimum number of threads to use (default 8)
  :max-queued-requests  - the maximum number of requests to be queued (default 1024)
  :thread-idle-timeout  - Set the maximum thread idle time. Threads that are idle
                          for longer than this period may be stopped (default 60000)"
  [handler {:keys [host port
                   min-threads max-threads
                   max-queued-requests thread-idle-timeout]
            :as options
            :or {host "127.0.0.1" port 8080
                 min-threads 8 max-threads 50
                 max-queued-requests 1024 thread-idle-timeout 60000}}]
  (let [^HttpServer server (HttpServer/create (InetSocketAddress. (str host) (int port)) 0)]
    (try
      (doto server
        (.setExecutor (ThreadPoolExecutor. min-threads
                                           max-threads
                                           thread-idle-timeout
                                           TimeUnit/MILLISECONDS
                                           (ArrayBlockingQueue. max-queued-requests)))
        (.createContext "/" (proxy [HttpHandler] []
                              (handle [exchange]
                                (handle-http-exchange exchange handler options))))
        .start)
      (catch Throwable t
        (stop-http-server server)
        (throw t)))))
