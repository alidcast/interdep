(ns interdep.multi-alias
  "Match multiple deps aliases based on configured profiles."
  (:require
   [interdep.impl.tools :as tools]
   [interdep.multi-repo :as mr]))

;; [Example]
;;  :interdep.multi-alias/profiles
;;     {:main {:alias-name* [:main]}}

;; [Note on profile alias matching]:
;; Contraints
;;  * The combined profiles must have at least one alias matcher configured.
;; Reasoning: 
;;  Profile matchers filter out (rather than filter in) aliases. 
;;  Therefore, if no matchers are passed, all aliases would be included.
;;  So we ensure at least one matcher is present as it's better to be explicit inclusions.

(defn- cleanse-deps
  "Remove any custom multi-repo keys from deps config."
  [deps]
  (dissoc deps
          :interdep.multi-repo/registry
          :interdep.multi-repo/includes))

;; --- Profile validations 

(defn validate-combined-profile-map!
  [{:keys [alias-ns* alias-name*] :as _profile} profile-keys]
  (when (and (empty? alias-ns*)
             (empty? alias-name*))
    (throw (tools/err "Combined profiles must have at least one alias matcher:" profile-keys)))
  :valid)

;; --- Alias profile matching 

(defn- combine-active-profiles
  "Combines active profile keys into a single profile config."
  [profiles profile-keys]
  (reduce
   (fn [acc k]
     (let [m    (get profiles k)
           p    (:path m)
           a-ns (:alias-ns* m)
           a-n  (:alias-name* m)
           o    (:extra-opts m)]
       (cond-> acc
         p    (assoc :path p)
         a-ns (update :alias-ns* into a-ns)
         a-n  (update :alias-name* into a-n)
         o    (update :extra-opts merge o))))
   {:path :default
    :alias-ns*   []
    :alias-name* []
    :extra-opts  {}}
   profile-keys))

(defn- match-deps-aliases
  "Matches any deps aliases keys if namespace and name is in alias-ns* and alias-name* vectors, respectively.
   An empty matching vector counts as a match for that check."
  [deps alias-ns* alias-name*]
  (reduce
   (fn [matches [alias-key]]
     (let  [a-ns (namespace alias-key)
            a-n  (name alias-key)]
       (if (and
            (or (empty? alias-ns*) (some #(= a-ns (-> % name str)) alias-ns*))
            (or (empty? alias-name*) (some #(= a-n (-> % name str)) alias-name*)))
         (conj matches alias-key)
         matches)))
   []
   (:aliases deps)))

(defn with-profiles
  "Match active aliases based on profiles in processed deps config.

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
     ::extra-opts    - matched profile extra-opts."
  ([deps] (with-profiles deps []))
  ([-processed-deps profile-keys]
   (tools/with-err-boundary "Error processing Interdep alias profiles."
     (let [{::mr/keys [main-deps root-deps subrepo-deps]} -processed-deps
           processed-deps (update -processed-deps ::mr/main-deps cleanse-deps)
           profiles (::profiles root-deps)]
       (if (seq profile-keys)
         (let [{:keys [path alias-ns* alias-name* extra-opts]
                :as combined-profile} (combine-active-profiles profiles profile-keys)]
           (validate-combined-profile-map! combined-profile profile-keys)
           (-> processed-deps
               (assoc ::matched-aliases
                      (match-deps-aliases
                       (if (= path :default) main-deps (get subrepo-deps path))
                       alias-ns*
                       alias-name*))
               (assoc ::extra-opts extra-opts)))
         (-> processed-deps
             (assoc ::matched-aliases [])
             (assoc ::extra-opts {})))))))
