(ns clj-teamcity-api.api
  (:require [clj-teamcity-api.net :as net]))

;; info

(defn projects [server auth]
  (net/rest-api-request server auth "projects"))

(defn project [server auth project-id]
  (->> project-id
       (format "projects/id:%s")
       (net/rest-api-request server auth)))

(defn build-type [server auth build-type-id]
  (->> build-type-id
       str
       (format "buildTypes/id:%s")
       (net/rest-api-request server auth)))

(defn last-builds [server auth build-type-id]
  (->> build-type-id
       str
       (format "builds?locator=buildType:%s,running:any")
       (net/rest-api-request server auth)))

(defn running-build [server auth build-type-id]
  (->> build-type-id
       str
       (format "builds?locator=buildType:%s,running:true")
       (net/rest-api-request server auth)))

(defn build [server auth build-id]
  (->> build-id
       str
       (format "builds/id:%s")
       (net/rest-api-request server auth)))

(defn vcs-roots [server auth project-id]
  (->> project-id
       (format "vcs-roots?locator=project:(id:%s)")
       (net/rest-api-request server auth)))

(defn vcs-root [server auth vcs-root-id]
  (->> vcs-root-id
       (format "vcs-roots/id:%s")
       (net/rest-api-request server auth)))

(defn vcs-root-instances [server auth vcs-root-id]
  (->> vcs-root-id
       (format "vcs-root-instances?locator=vcsRoot:(id:%s)")
       (net/rest-api-request server auth)))

(defn vcs-root-instance [server auth vcs-root-instance-id]
  (->> vcs-root-instance-id
       str
       (format "vcs-root-instances/id:%s")
       (net/rest-api-request server auth)))

(defn build-type-changes [server
                          auth
                          build-type-id
                          vsc-root-instance-id
                          since-build-id]
  (->> (format "changes?locator=buildType:(id:%s),vcsRootInstance:(id:%s),sinceChange:(build:%s)"
               (str build-type-id)
               (str vsc-root-instance-id)
               (str since-build-id))
       (net/rest-api-request server auth)))

(defn build-type-queue [server auth build-type-id]
  "build queue for given agent"
  (->> build-type-id
       str
       (format "buildQueue?locator=buildType:%s")
       (net/rest-api-request server auth)))

(defn project-queue [server auth project-id]
  (->> project-id
       (format "buildQueue?locator=project:%s")
       (net/rest-api-request server auth)))

(defn test-occurences [server auth build-id offset limit]
  "test statistics for given build"
  (->> (format "testOccurrences?locator=build:(id:%s),start:%s,count:%s"
               (str build-id)
               (str offset)
               (str limit))
       (net/rest-api-request server auth)))

(defn agents [server auth]
  (->> (format "agents")
       (net/rest-api-request server auth)))

;; actions

(defn trigger-build [server auth build-type-id]
  (let [body (format "<build><buildType id=\"%s\" /></build>" build-type-id)]
    (net/post-xml server auth "buildQueue" body)))

(defn cancel-build [server auth running-build-id]
  (let [body "<buildCancelRequest comment='stop' readdIntoQueue='false' />"]
    (net/post-xml server auth (format "builds/id:%s" running-build-id) body)))

(defn reboot-agent [server auth agent-id]
  (net/post-plain server auth (net/reboot-url server agent-id) nil))
