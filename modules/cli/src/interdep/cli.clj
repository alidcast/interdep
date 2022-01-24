(ns interdep.cli
  "Interdep command argument utility."
  (:require [clojure.string :as str]
            [interdep.multi-repo :as multi-repo]
            [interdep.multi-alias :as multi-alias]))

;; [Paring of arguments note:]
;; We're using Regex rather than tools.cli [parse-opt] helper because
;; the intention is to replace our custom arguments and foward the rest 
;; to tools.deps. Since ordering of arguments matters, easier to use Regex.

(def profile-pattern #"-P(:[a-zA-Z]+)?")
(def main-pattern  #"-M(:[a-zA-Z]+)?")

(defn- kw-str->kw-vec
  "Convert option string of keywords to a vector."
  [s]
  (mapv #(-> % (str/replace ":" "") keyword)
        (re-seq #":[^:]+" s)))

(defn pr-cli [x]
  (str "'" (pr-str x) "'"))


(defn parse-opt [args pattern]
  (when-let [s (->> args
                    (re-find pattern)
                    (first))]
    (kw-str->kw-vec s)))

(defn cmd-args [cli-args]
  (let [str-args (str/join " " cli-args)
        profile-keys (parse-opt str-args profile-pattern)
        alias-keys (parse-opt str-args main-pattern)
        {::multi-repo/keys  [main-deps]
         ::multi-alias/keys [matched-aliases]} (-> (multi-repo/process-deps)
                                                   (multi-alias/with-profiles profile-keys nil))
        aliases (into matched-aliases alias-keys)]
    (println "Matched aliases:" matched-aliases)
    (str/join
     " "
     ["-Sdeps" (pr-cli main-deps)
      (-> str-args
          (str/replace profile-pattern "")
          (str/replace main-pattern (apply str "-M" aliases)))])))


