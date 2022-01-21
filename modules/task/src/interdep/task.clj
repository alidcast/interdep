(ns interdep.task
  (:require [babashka.process :as proc]
            [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [interdep.multi-repo :as multi-repo]
            [interdep.multi-alias :as multi-alias]))

(def cli-opts
  [;; custom 
   ["-P" "--profiles PROFILES" "Match aliases based on configured profiles."]
   ;; --tools.deps
   ["-M" "--main-aliases ALIASES" "Aliases to foward to main command."]
   ["-m" "--main-args ARGS" "Arguments to forward to main command."]
   ["-h" "--help" "Print help instructions." :default false]])

(defn handle-cmd
  "Handle command `f` with given `args`."
  [args f]
  (let [{:keys [arguments options summary]} (cli/parse-opts args cli-opts)]
    (if (:help options)
      (println summary)
      (f arguments options))))

(defn- kw-str->kw-vec
  "Convert string of keywords to a vector."
  [s]
  (if s
    (mapv #(-> % (str/replace ":" "") keyword)
          (re-seq #":[^:]+" s))
    []))

(defn start [cli-args]
  (handle-cmd
   cli-args
   (fn [args opts]
     (let [path (first args)
           {:keys [main-aliases main-args profiles]} opts
           profile-keys  (kw-str->kw-vec profiles)
           alias-keys    (kw-str->kw-vec main-aliases)
           {::multi-repo/keys  [main-deps]
            ::multi-alias/keys [matched-aliases]} (-> (multi-repo/process-deps)
                                                      (multi-alias/with-profiles profile-keys path))]
       (println "Matched aliases:" matched-aliases)
       (-> (proc/process
            (cond-> ["clojure" "-Sdeps" (pr-str main-deps) (apply str "-M" (into matched-aliases alias-keys))]
              main-args (conj main-args))
            {:inherit true
             :shutdown proc/destroy})
           (proc/check))))))
