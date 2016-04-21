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

(defn rest-api-url [server]
  (format "http://%s:%d/httpAuth/app/rest/"
          (str (:host server))
          (:port server)))

(defn reboot-url [server build-type-id]
  (format "http://%s:%d/remoteAccess/reboot.html?agent=%s&rebootAfterBuild=false"
          (str (:host server))
          (:port server)
          build-type-id))

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
