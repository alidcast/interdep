(ns interdep.deps
  (:require
   [clojure.java.io :as io]
   [interdep.deps.api :as api]))

(defn- cleanse-deps 
  [deps]
  (dissoc deps :interdep/config))

(defn- resolve-plugin! 
  [pns]
  (when-not (find-ns pns) (throw (ex-info "Plugin namespace could not be found." {:ns pns})))
  (if-let [plugin-var (resolve (symbol (str pns) "plugin"))]
    @plugin-var
    (throw (ex-info "Plugin namespace missing its function." {:ns pns}))))

(defn process 
  []
  (let [{:interdep/keys [config]
         :as root-deps}  (api/read-root-deps)
        plugins     (:plugins config)
        out-deps (cleanse-deps root-deps)
        ctx {:root-deps root-deps
             :out-deps out-deps}]
    (reduce
     (fn [ctx sym]
       ((resolve-plugin! sym) ctx))
     ctx 
     plugins)))

(comment
  (with-redefs [api/read-root-deps (constantly {:interdep/config {:plugins ['interdep.plugins.multi-repo]}
                                                :interdep.multi-repo/registry ["subrepo"]})
                api/read-sub-deps  (constantly {:aliases {:my/foo "bar"}})]
    (process)))
