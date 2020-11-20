(ns interdep.multi-alias
  "Match multiple deps aliases based on configured profiles."
  (:require [interdep.impl.cli :as cli]))

(defn- cleanse-deps
  "Remove any custom multi-alias keys from deps config."
  [deps]
  (dissoc deps :interdep.multi-alias/profiles))

;;;; Profile validations 

;; Note: Since the profile alias matchers filter out aliases, when no matchers are passed 
;; all aliases would be included. This can be suprising if multiple profiles are combined,
;; so we validate that the final profile include's at least one alias filter, as it's 
;; better to be explicit inclusions.

(defn validate-combined-profile-map!
  [{:keys [alias-ns* alias-name*] :as _profile} profile-keys]
  (when (and (empty? alias-ns*)
             (empty? alias-name*))
    (throw (cli/err "Combined profiles must have at least one alias matcher:" profile-keys)))
  :valid)

;;;; Alias profile matching 

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
  ([deps] (use-profiles deps []))
  ([-processed-deps profile-keys]
   (cli/with-err-boundary "Error processing multi-alias profiles."
     (let [{:keys [out-deps root-deps subrepo-deps]} -processed-deps
           processed-deps (update -processed-deps :out-deps cleanse-deps)
           profiles (::profiles root-deps)]
       (if (seq profile-keys)
         (let [{:keys [path alias-ns* alias-name* extra-opts]
                :as combined-profile} (combine-active-profiles profiles profile-keys)]
           (validate-combined-profile-map! combined-profile profile-keys)
           (-> processed-deps
               (assoc ::matched-aliases
                      (match-deps-aliases
                       (if (= path :default) out-deps (get subrepo-deps path))
                       alias-ns*
                       alias-name*))
               (assoc ::extra-options extra-opts)))
         (-> processed-deps
             (assoc ::matched-aliases [])
             (assoc ::extra-options {})))))))
