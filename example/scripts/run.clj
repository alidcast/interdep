#!/usr/bin/env bb

(require '[clojure.java.io :as io])
(require '[clojure.tools.cli :as cli])
(require '[clojure.pprint :as ppr])
(require '[interdep.multi-repo :as multi-repo])
(import java.lang.ProcessBuilder$Redirect)

(def cli-opts
  [["-M" "--main-aliases ALIASES" "Forward aliases to clojure main command."]
   ["-h" "--help" "Print help instructions." :default false]])

(defn- before-runtime-exit 
  "Add a `f` to be called before runtime exits."
  [f]
  (-> (Runtime/getRuntime)
      (.addShutdownHook (Thread. f))))

(defn- start-process
  "Starts shell process that calls given `command` from working directory `path`."
  [cmd path]
  ;; see babashka book for details on creating java processes
  ;; https://book.babashka.org/#_java_lang_processbuilder
  (let [pb  (doto (ProcessBuilder. cmd)
              (.redirectOutput ProcessBuilder$Redirect/INHERIT))
        proc (-> pb
                 (doto (.directory (io/file path)))
                 .start)]
    (before-runtime-exit #(.destroy proc))
    proc))

(defn handle-cli
  "Handle main cli command `f`."
  [f]
  (let [args *command-line-args*
        {:keys [arguments options summary]} (cli/parse-opts args cli-opts)]
    (if (:help options)
      (println summary)
      (f arguments options))))

(defn ppr-str
  "Convert edn to a pretty string.
   Note: pr-str does output data with newlines characters, so we use output of pprint instead."
  ([edn]
   (with-out-str
    ;; note: in a clojure program, we were setting *print-namespace-maps* to false since namespaced maps aren't common
    ;; in deps.edn files,  but in babashka that binding isn't defined and therefore is already falsy.
     (ppr/pprint edn))))


;; Print processed deps to .main/deps.edn and then call clojure cli from there
(handle-cli
 (fn [_args opts]
   (let [out-dir  ".main"
         out-file ".main/deps.edn"
         deps (multi-repo/process {:root-dir "../"})]
     (io/make-parents out-file)
     (spit out-file (ppr-str deps))
     (->
      ["clj" (str "-M" (:main-aliases opts))]
      (start-process out-dir)
      ;; Wait for process to finish and then exit.
      .waitFor
      System/exit))))
