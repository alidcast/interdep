#!/usr/bin/env bb

(require '[babashka.process :as proc])
(require '[clojure.java.io :as io])
(require '[clojure.tools.cli :as cli])
(require '[clojure.pprint :as ppr])
(require '[clojure.string :as str])
(require '[interdep.multi-repo :as multi-repo])
(require '[interdep.multi-alias :as multi-alias])

(def cli-opts
  [["-M" "--main-aliases ALIASES" "Forward aliases to clojure main command."]
   ["-h" "--help" "Print help instructions." :default false]])

(defn- handle-cli
  "Handle main cli command `f`."
  [f]
  (let [args *command-line-args*
        {:keys [arguments options summary]} (cli/parse-opts args cli-opts)]
    (if (:help options)
      (println summary)
      (f arguments options))))

(defn- ppr-str
  "Convert edn to a pretty string.
   Note: pr-str does output data with newlines characters, so we use output of pprint instead."
  ([edn]
   (with-out-str
    ;; note: in a clojure program, we were setting *print-namespace-maps* to false since namespaced maps aren't common
    ;; in deps.edn files,  but in babashka that binding isn't defined and therefore is already falsy.
     (ppr/pprint edn))))

(defn- kw-str->kw-vec [s]
  (if s
   (mapv #(-> % (str/replace ":" "") keyword)
         (re-seq #":[^:]+" s))
    []))

;; Print processed deps to .main/deps.edn and then call clojure cli from there
(handle-cli
 (fn [args _]
   (let [out-dir  ".main"
         out-file ".main/deps.edn"
         profile-keys  (kw-str->kw-vec (first args))
         alias-keys    (kw-str->kw-vec (second args))
         {::multi-repo/keys  [main-deps]
          ::multi-alias/keys [matched-aliases]} (-> (multi-repo/process-deps {:out-dir out-dir})
                                                    (multi-alias/with-profiles profile-keys))]
     (io/make-parents out-file)
     (spit out-file (ppr-str main-deps))
     (-> (proc/process
          ["clj" (apply str "-M" (into matched-aliases alias-keys))]
          {:inherit true
           :dir out-dir
           :shutdown #(.destroy %)})
         (proc/check)))))
