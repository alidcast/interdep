#!/usr/bin/env bb

(require '[babashka.process :as proc])
(require '[clojure.tools.cli :as cli])
(require '[clojure.string :as str])
(require '[interdep.multi-repo :as multi-repo])
(require '[interdep.multi-alias :as multi-alias])

(def cli-opts
  [["-h" "--help" "Print help instructions." :default false]])

(defn- handle-cli
  "Handle main cli command `f`."
  [f]
  (let [args *command-line-args*
        {:keys [arguments options summary]} (cli/parse-opts args cli-opts)]
    (if (:help options)
      (println summary)
      (f arguments options))))

(defn- kw-str->kw-vec
  "Convert string of keywords to vector of keywords."
  [s]
  (if s
    (mapv #(-> % (str/replace ":" "") keyword)
          (re-seq #":[^:]+" s))
    []))

(handle-cli
 (fn [args _]
   (let [profile-keys  (kw-str->kw-vec (first args))
         alias-keys    (kw-str->kw-vec (second args))
         {::multi-repo/keys  [sub-deps]
          ::multi-alias/keys [matched-aliases]} (-> (multi-repo/process-deps)
                                                    (multi-alias/with-profiles profile-keys))]
     (-> (proc/process
          ["clojure" "-Sdeps" (pr-str sub-deps) (apply str "-M" (into matched-aliases alias-keys))]
          {:inherit true
           :shutdown proc/destroy})
         (proc/check)))))
