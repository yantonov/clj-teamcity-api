(ns clj-teamcity-api.net
  (:require [clj-http.client :as http]
            [clojure.xml :as xml])
  (:import [java.io ByteArrayInputStream]
           [java.nio.charset Charset]))

(defrecord Credentials [user pass])

(defrecord TeamCityServer [host port])

(defn make-credentials [user pass]
  (Credentials. user pass))

(defn make-server [host & {:keys [port] :or {port 80}}]
  (TeamCityServer. host port))

(defn detect-schema [host]
  (let [lhost (.toLowerCase host)]
    (cond
      (.startsWith lhost "http://") "http://"
      (.startsWith lhost "https://") "https://"
      true "http://")))

(defn normalize-host [host]
  (let [schema (detect-schema host)
        normalized (if (.startsWith host schema)
                     (.substring host (.length schema))
                     host)]
    (str schema normalized)))

(defn rest-api-url [server]
  (let [{host :host
         port :port
         :or {port 80}} server]
    (format "%s:%d/httpAuth/app/rest/" (normalize-host host) port)))

(defn reboot-url [server build-type-id]
  (let [{host :host
         port :port
         :or {port 80}} server]
    (format "%s:%d/remoteAccess/reboot.html?agent=%s&rebootAfterBuild=false"
            (normalize-host host)
            port
            build-type-id)))

(defn rest-api-request [server
                        auth
                        relative-url]
  (let [response (-> (str (rest-api-url server)
                          relative-url)
                     (http/get {:basic-auth [(:user auth)
                                             (:pass auth)]})
                     :body)]
    (try (-> response
             (.getBytes (Charset/forName "UTF-8"))
             (ByteArrayInputStream.)
             (xml/parse))
         (catch Exception e
           (throw (java.lang.IllegalStateException.
                   (format "cant parse response:\n%s" response)
                   e))))))

(defn post-xml [server auth relative-url body]
  (-> (str (rest-api-url server)
           relative-url)
      (http/post {:basic-auth [(:user auth)
                               (:pass auth)]
                  :content-type "application/xml"
                  :body body})))

(defn post-plain [server auth relative-url body]
  (-> relative-url
      (http/post {:basic-auth [(:user auth)
                               (:pass auth)]
                  :content-type "text/plain"
                  :body body})))
