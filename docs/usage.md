# Interdep Usage

Interdep only provides utilities for processing deps configuration, and leaves up to you the use of those processed deps as the basis for Clojure programs. 

In order to use the processed deps, you have two options: 
1) Generate a deps.edn file and start Clojure from its directory.
2) Include the processed subproject deps as a Clojure cli `-Sdeps` argument.

This can be scripted in Bash, or with tools like [Babashka](https://github.com/borkdude/babashka). See this library's [example](/example) for a working reference.

Do note, you'll likely want to run your program from the root of your project, not from another subdirectory. This way, any tool that relies on files relative to root directory will work as usual. So, if you take approach 1), you'll likely need to configure your deps in another file that is not named `deps.edn` (as that one will be auto-generated). For that reason, by default, we recommend approach 2).

### Basic Example

Interdep exposes the `multi-repo/process-deps` namespace for unifying local subrepo deps into a single config, and the `multi-alias/with-profiles` namespace for matching multiple aliases based on configured profiles. These functions are meant to be threaded together and return a namespaced context map.

An example showing basic usage:
```clj
(require '[interdep.multi-repo :as multi-repo])
(require '[interdep.multi-alias :as multi-alias])

(defn interdep-basic-example
  [{:keys [out-dir profile-keys]}]
 (let [{::multi-repo/keys  [main-deps]
        ::multi-alias/keys [matched-aliases]} (-> (multi-repo/process-deps {:out-dir out-dir})
                                                  (multi-alias/with-profiles profile-keys))]
   (spit (str out-dir "/deps.edn") main-deps)
   (start-clojure-program out-dir matched-aliases)))

;; note: below we're hard-coding the profile-keys, but in actual usage they'd be a parsed cli argument.
(interdep-basic-example ".main" [:dev])
```

### Configuring Multiple Subrepos

You can register subproject deps using the `:interdep.multi-repo/registry` config property in your root deps config.

In those subprojects, you can then declare `local:root` deps as usual. Any processed local deps may be properly qualified when processing.

Some intentional constraints to be aware of:
- Subrepos are only allowed to declare namespaced aliases. So, for example, instead of having a `:dev` alias, use `:[subrepo]/dev`. This makes it simple to merge multiple aliases and use them together from the root repository.
- Subrepos can only declare paths via alias `:extra-paths` and `:extra-deps`, not top-level `:paths` or `:deps`. These other paths are auto-included when starting a program, with a single repo that's desired, but in a monorepo, with multiple unified configs, that behavior is inflexible. It's better to explicitly load the subrepo aliases you need.
- Subrepos are only allowed to declare local deps that are inside root repo. If you need to load deps outside root repo, e.g. while development external libraries, you can configure that in your cross project deps file (typically found in `~/.clojure/deps.edn`).

As an example, say an application has the following directory structure: 
```
- rejoice-app
- rejoice-api
- rejoice-model
deps.edn
```

The `deps.edn` configs could be as follows:
```clj
;; root deps.edn
{:interdep.multi-repo/registry
 ["rejoice-app"
  "rejoice-api"
  "rejoice-model"]

;; app deps.edn
{:aliases 
 {:app/main 
  {:extra-deps {rejoice-cljc/model {:local/root "../rejoice-model"}}}}}   

;; api deps.edn
{:aliases 
 {:api/main 
  {:extra-deps {rejoice-cljc/model {:local/root "../rejoice-model"}}}}}
```

You'd then use`interdep.multi-repo/process-deps` to unify local subrepo deps into a single config. 

`interdep.multi-repo/process-deps` returns a map of: 
 - `::main-deps`, the unified root and sub-deps config.
 - `::main-deps`, the unified sub-deps config.
 - `::root-deps`, the root deps config.
 - `::subrepo-deps`, mapping of subproject deps configs.

So, for the above deps config, `interdep.multi-repo/process-deps` would return a `::main-deps` of: 
```clj
{:aliases 
 {:app/main 
  {:extra-deps {rejoice-cljc/model {:local/root "../rejoice-model"}}}
  :api/main 
  {:extra-deps {rejoice-cljc/model {:local/root "../rejoice-model"}}}}}
```

With the processed deps config, you could call `clj -M:app/main:api/main` to run your project. Though listing out multiple namespaced aliases can get tedious, which is why the next namespace, `interdep.multi-alias`,  provides a better approach.

### Configuring Multiple Aliases

Instead of explicitly passing aliases, profiles let you filter out which ones not to include. This is useful when you have multiple subrepos with multiple aliases and you want to run several of those aliases together.

You can configure alias profiles in your root deps.edn using the `:interdep.multi-alias/profiles` config property.

Profile options:
- `:path` - subrepo path to match aliases from. Defaults to matching from unified deps config.
- `:alias-ns*` - vector of alias key namespaces to match at least one of.
- `:alias-name*` - vector of alias key names to match at least one of.
- `:extra-opts` - extra options to include in context when profile is activated.

As an example, a config may look like:
```clj
;; root deps.edn
 :interdep.multi-alias/profiles
 {:app  {:path "rejoice-app"}
  :api  {:path "rejoice-api"}  
  :dev  {:alias-name* [:main :local]
  :prod {:alias-name* [:main]}}

;; app deps.edn
{:aliases 
 {:app/main {,,,}
  :app/local {,,,}}}   

;; api deps.edn
{:aliases 
 {:api/main {,,,}
  :api/local {,,,}}
```

You'd then use `interdep.multi-alias/with-profiles` to get a list of matching alias configurations based on active profiles.

`interdep.multi-alias/with-profiles` returns map with following associated keys:
- `::matched-aliases`, matched aliases based on passed profile keys and configured profile options.
- `::extra-opts`, merged map of any `:extra-opts` in activated profiles.

With the above config, you could combine profiles depending on what aliases you wanted to use:
- With the `:dev` profile only, aliases `:app/main`, `:app/local`, `:api/main`, and `api:local` would be used.
- With the `:prod` profile only, aliases `:app/main` and `:api/main` would be used.
- With the `:app` and `:dev` combined, ony `:app/main` and `app/local` would be used.
