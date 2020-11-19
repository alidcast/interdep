(ns interdep.multi-alias
  "Match multiple deps aliases based on configured profiles.")

(defn- cleanse-deps
  "Remove any custom multi-alias keys from deps config."
  [deps]
  (dissoc deps :interdep.multi-alias/profiles))

;;;; Profile validations 

;; Note: 
;; s

(defn validate-combined-profile-map!
  [{:keys [alias-ns* alias-name*] :as _profile}]
  (when (and (empty? alias-ns*)
             (empty? alias-name*))
    (throw (ex-info "Combined profile must have at least one alias filter." {:profile _profile})))
  :valid)

;;;; Alias profile matching 

(defn- combine-active-profiles
  "Combines active profile keys into a single profile config."
  [profiles profile-keys]
  (let [path             (atom :default)
        alias-namespaces (atom [])
        alias-names      (atom [])
        extra-opts       (atom {})]
    (doseq [k profile-keys]
      (let [m (get profiles k)]
       (when-let [p (:path m)]
         (reset! path p))
       (when-let [namespaces (:alias-ns* m)]
         (swap! alias-namespaces into namespaces))
       (when-let [names (:alias-name* m)]
         (swap! alias-names into names))
       (when-let [opts (:extra-opts m)]
         (swap! alias-names merge opts))))
    {:path        @path
     :alias-ns*   @alias-namespaces
     :alias-name* @alias-names
     :extra-opts  @extra-opts}))

(defn- match-deps-aliases
  "Matches any deps aliases keys if namespace and name is in alias-ns* and alias-name* vectors, respectively.
   An empty matching vector counts as a match for that check."
  [deps alias-ns* alias-name*]
  (reduce
   (fn [matches [alias-key]]
     (let  [a-ns (namespace alias-key)
            a-n  (name alias-key)]
       (println a-ns a-n alias-ns* alias-name*)
       (if (and
            (or (empty? alias-ns*) (some #(= a-ns (-> % name str)) alias-ns*))
            (or (empty? alias-name*) (some #(= a-n (-> % name str)) alias-name*)))
         (do (println "matched") (conj matches alias-key))
         matches)))
   []
   (:aliases deps)))

(defn use-profiles
  "Match active aliases in a processed deps config, based on passed profile keys.
   
   Profile map options: 
     :path         - Path to match aliases from. Defaults to :main. 
                     On conflict: last wins.
     :alias-ns*    - Alias namespaces to match for. 
                     On conflict: conjoined.
     :alias-name*  - Alias names to match for. 
                     On conflict: conjoined.
     :extra-opts   - Extra options to include in result. 
                     On conflict: merged.
   
   Returns processed deps, with following extra properties:
     ::matched-aliases  - matched profile aliases.
     ::extra-options    - matched profile extra-options."
  ([deps] (use-profiles deps {}))
  ([-processed-deps profile-keys]
   (let [{:keys [out-deps root-deps subrepo-deps]} -processed-deps
         processed-deps (update -processed-deps :out-deps cleanse-deps)
         profiles (::profiles root-deps)]
     (if (seq profile-keys)
       (let [{:keys [path alias-ns* alias-name* extra-opts]
              :as profile} (combine-active-profiles profiles profile-keys)]
         (validate-combined-profile-map! profile)
         (-> processed-deps
             (assoc ::matched-aliases
                    (match-deps-aliases
                     (if (= path :default) out-deps (get subrepo-deps path))
                     alias-ns*
                     alias-name*))
             (assoc ::extra-options extra-opts)))
       (-> processed-deps
           (assoc ::matched-aliases [])
           (assoc ::extra-options {}))))))
