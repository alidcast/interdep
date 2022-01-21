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

(def cli-opts
  [;; -- tools.deps opts

   ["-M" "--aliases ALIASES"
    "Aliases to foward to main command."
    :parse-fn  kw-str->kw-vec
    :default []]

   ;; -- custom opts

   ["-P" "--profiles PROFILES"
    "Match aliases based on configured profiles."
    :parse-fn  kw-str->kw-vec
    :default []]

   ["-h" "--help"
    "Print help instructions."
    :default false]])

(defn clojure-only-cli-opt?
  [s]
  (and (str/starts-with? s "-")
       (not (or (.contains s "-P")
                (.contains s "-M")))))

(defn handle-cmd
  "Handle command `f` with given `args`."
  [cli-args f]
  (let [{:keys [arguments options summary]} (cli/parse-opts cli-args cli-opts)
        extra-opts   (filterv clojure-only-cli-opt? cli-args)]
    (if (:help options)
      (println summary)
      (f arguments options extra-opts))))

(defn cmd-args [cli-args]
  (handle-cmd
   cli-args
   (fn [args opts extra-opts]
     (let [path (first args)
           {:keys [aliases profiles main-args]} opts
           {::multi-repo/keys  [main-deps]
            ::multi-alias/keys [matched-aliases]} (-> (multi-repo/process-deps)
                                                      (multi-alias/with-profiles profiles path))
           aliases (into matched-aliases aliases)]
       (println "Matched aliases:" matched-aliases)  
       (println extra-opts main-args)
       (str/join
        " "
        (cond-> ["-Sdeps" (pr-cli main-deps)]
          (seq extra-opts) (into extra-opts)
          (seq aliases) (conj (apply str "-M" aliases))
          main-args  (conj main-args)))))))
