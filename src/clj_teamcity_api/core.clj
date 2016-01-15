(ns clj-teamcity-api.core
  (:require [clj-http.client :as http]
            [clojure.pprint :as pprint]
            [clojure.xml :as xml])
  (:import [java.io ByteArrayInputStream]))

(defrecord Credentials [user pass])

(defrecord TeamCityServer [host port])

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
             (.getBytes)
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

(defn tag? [tag-name]
  #(= tag-name (:tag %)))

(defn empty-list-if-nil [x]
  (if (nil? x) '() x))

(defn projects [server auth]
  (rest-api-request server auth "projects"))

(defn find-project [server auth name]
  (->> (projects server auth)
       :content
       (filter (fn [project]
                 (= name (get-in project [:attrs :name]))))
       first
       :attrs))

(defn project [server auth project-id]
  (let [info (->> project-id
                  (format "projects/id:%s")
                  (rest-api-request server auth))
        build-types (->> info
                         :content
                         (filter (tag? :buildTypes))
                         first)
        build-types-ids (->> build-types
                             :content
                             (map #(get-in % [:attrs :id])))]
    build-types-ids
    {:build-type-ids (empty-list-if-nil build-types-ids)}))

(defn build-type [server auth build-type-id]
  (->> build-type-id
       (format "buildTypes/id:%s")
       (rest-api-request server auth)))

(defn last-builds [server auth build-type-id]
  (->> build-type-id
       (format "builds?locator=buildType:%s,running:any")
       (rest-api-request server auth)
       :content
       (map :attrs)))

(defn running-build [server auth build-type-id]
  (->> build-type-id
       (format "builds?locator=buildType:%s,running:true")
       (rest-api-request server auth)
       :content
       (map :attrs)
       first))

(defn build [server auth build-id]
  (let [info (->> build-id
                  str
                  (format "builds/id:%s")
                  (rest-api-request server auth)
                  :content)
        test-occurences (->> info
                             (filter (tag? :testOccurrences))
                             first
                             :attrs)
        changes (->> info
                     (filter (tag? :lastChanges))
                     first
                     :content
                     (map :attrs))
        status-text (->> info
                         (filter (tag? :statusText))
                         first
                         :content
                         first)
        agent (->> info
                   (filter (tag? :agent))
                   first
                   :attrs)]
    {:test-occurences (empty-list-if-nil test-occurences)
     :changes (empty-list-if-nil changes)
     :status-text status-text
     :agent agent}))

(defn trigger-build [server auth build-type-id]
  (let [body (format "<build><buildType id=\"%s\" /></build>" build-type-id)]
    (post-xml server auth "buildQueue" body)))

(defn cancel-build [server auth build-type-id]
  (let [running-build-id (:id (running-build server auth build-type-id))]
    (if (not (nil? running-build-id))
      (let [body "<buildCancelRequest comment='stop' readdIntoQueue='false' />"]
        (post-xml server auth (format "builds/id:%s" running-build-id) body)))))

(defn reboot-agent [server auth build-type-id]
  (let [last-build-id (:id (first (last-builds server auth build-type-id)))
        last-build (build server auth last-build-id)
        agent-id (get-in last-build [:agent :id])]
    (post-plain server auth (reboot-url server agent-id) nil)))

;; (defn vcs-roots [server auth project-id]
;;   (->> project-id
;;        (format "vcs-roots?locator=project:(id:%s)")
;;        (rest-api-request server auth)
;;        :content
;;        (filter (tag? :vcs-root))
;;        (map :attrs)))

;; (defn vcs-root [server auth vcs-root-id]
;;   (let [root   (->> vcs-root-id
;;                     (format "vcs-roots/id:%s")
;;                     (rest-api-request server auth)
;;                     :content)
;;         properties (->> root
;;                         (filter (tag? :properties))
;;                         first
;;                         :content
;;                         (filter (tag? :property))
;;                         (map (fn [tag]
;;                                (let [attrs (:attrs tag)]
;;                                  (vector (keyword (:name attrs))
;;                                          (:value attrs)))))
;;                         (apply concat)
;;                         (apply hash-map))]

;;     properties))

;; (defn vcs-root-instances [server auth vcs-root-id]
;;   (->> vcs-root-id
;;        str
;;        (format "vcs-root-instances?locator=vcsRoot:(id:%s)")
;;        (rest-api-request server auth)
;;        :content
;;        (map :attrs)))


;; (defn test-occurences [server auth build-id offset limit]
;;   "test statistics for given build"
;;   (->> (format "testOccurrences?locator=build:(id:%s),start:%s,count:%s"
;;                (str build-id)
;;                (str offset)
;;                (str limit))
;;        (rest-api-request server auth)
;;        :content
;;        (filter (filter (tag? :testOccurrences))
;;                (map :attrs))))

;; (defn build-type-build-queue [server auth build-type-id]
;;   "build queue for given agent"
;;   (->> build-type-id
;;        str
;;        (format "buildQueue?locator=buildType:%s")
;;        (rest-api-request server auth)
;;        :content
;;        (map :attrs)))

;; (defn project-build-queue [server auth project-id]
;;   (->> project-id
;;        (format "buildQueue?locator=project:%s")
;;        (rest-api-request server auth)
;;        :content
;;        (map :attrs)))

;; (defn build-type-changes [server
;;                           auth
;;                           build-type-id
;;                           vsc-root-instance-id
;;                           since-build-id]
;;   "pending changes"
;;   (->> (format "changes?locator=buildType:(id:%s),vcsRootInstance:(id:%s),sinceChange:(build:%s)"
;;                (str build-type-id)
;;                (str vsc-root-instance-id)
;;                (str since-build-id))
;;        (rest-api-request server auth)))

