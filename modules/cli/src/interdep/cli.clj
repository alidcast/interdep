(ns interdep.cli
  "Interdep command argument utility."
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [interdep.multi-repo :as multi-repo]
            [interdep.multi-alias :as multi-alias]))

(defn- kw-str->kw-vec
  "Convert string of keywords to a vector."
  [s]
  (mapv #(-> % (str/replace ":" "") keyword)
        (re-seq #":[^:]+" s)))

(defn pr-cli [x]
  (str "'" (pr-str x) "'"))

;; :: When adding a new option update [clojure-only-cli-opt?] as well. 
(def cli-opts
  [;; -- tools.deps opts

   ["-m" "--main-cmd MAIN"
    "Argument to forward to main command."]

   ["-M" "--aliases ALIASES"
    "Aliases to foward to main command."
    :parse-fn  kw-str->kw-vec
    :default []]

   ;; -- custom opts

   ["-p" "--path PATH"
    "Directory paths to activate"]

   ["-P" "--profiles PROFILES"
    "Match aliases based on configured profiles."
    :parse-fn  kw-str->kw-vec
    :default []]

   ["-h" "--help"
    "Print help instructions."
    :default false]])

(defn clojure-only-cli-opt?
  "Check if it's an extra tools.deps option that is unparsed by cli."
  [s]
  (and (some? (re-matches #"^-[a-zA-Z]+" s))
       (not (or (.contains s "-m")
                (.contains s "-M")
                (.contains s "-p")
                (.contains s "-P")))))

(defn cmd-arg-opt?
  "Check if it's an opt with double hyphen (e.g. --watch), which are passed to custom commands."
  [s]
  (some? (re-matches #"^--[a-zA-Z]+" s)))

(defn handle-cmd
  "Handle command `f` with given `args`."
  [cli-args f]
  (let [{:keys [arguments options summary]} (cli/parse-opts cli-args cli-opts)
        dep-opts (filterv clojure-only-cli-opt? cli-args)
        arg-opts   (filterv cmd-arg-opt? cli-args)]
    (if (:help options)
      (println summary)
      (f options
         dep-opts
         (into arguments arg-opts)))))

(defn cmd-args [cli-args]
  (handle-cmd
   cli-args
   (fn [opts dep-opts args]
     (let [{:keys [path aliases profiles main-cmd]} opts
           {::multi-repo/keys  [main-deps]
            ::multi-alias/keys [matched-aliases]} (-> (multi-repo/process-deps)
                                                      (multi-alias/with-profiles profiles path))
           aliases (into matched-aliases aliases)]
       (println "Matched aliases:" matched-aliases)
       (str/join
        " "
        (cond-> ["-Sdeps" (pr-cli main-deps)]
          (seq dep-opts) (into dep-opts)
          (seq aliases) (conj (apply str "-M" aliases))
          ;; :: main command and its arguments must come last 
          main-cmd  (conj (str "-m " main-cmd))
          args (into args)))))))
