# Interdep

Interdep helps you intercept your repo's `deps.edn` configuration(s) before starting your Clojure program, so as to provide a sane, documented way of managing monorepo projects with Clojure's [tools.deps](https://github.com/clojure/tools.deps.alpha).

**Why can't monorepos be managed with tools.deps alone?**

While tools.deps lets you configure local dependencies via `:local/root` property, it does not allow you to include sub-project aliases from the root project. So, for example, if a subproject has a `:dev` alias, it is not called unless your starting the Clojure program from that project's directory. This is limiting, since you either have to declare all aliases in the root of the project or call a command only from the nested subproject. Lastly, using local file path dependencies is not tenable if your deployment needs to be reproducible (such as when deploying Datomic Ions).

## Status

Experimental/Alpha. But the usage is working, and scope is small, so API is unlikely to change much. Use git lib for now.

## Documentation

Interdep only provides utilities for processing deps configurations, it does not start your Clojure program for you. The intended usage of Interdep is as follows:

1) Process your project's `deps.edn` configuration(s).
2) Use the processed deps output as the basis for Clojure program commands.

Steps 1) is done using the Interdep utilities below. Step 2) can be scripted in Bash, or with tools like [Babashka](https://github.com/borkdude/babashka). Just `spit` out a deps.edn file and run your Clojure program from its directory. See this library's [example](https://github.com/rejoice-cljc/interdep/tree/master/example) for a working reference.

### Usage

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

### Multiple Subrepos

The `interdep.multi-repo` namespace unifies local subrepo deps into a single config. 

Usage:

- Register nested repository paths in your root deps.edn using the :interdep.multi-repo/registry config property.
- Call `interdep.multi-repo/process` to get deps config that combines your root and registered repository configurations.
- Declare `local:root` deps as usual. This way you can still start a Clojure program from a subrepo without any processing, since any processed local deps may be properly qualified based on `:out-dir` option.

Some intentional constraints to be aware of:
- Subrepos are only allowed to declare namespaced aliases. So, for example, instead of having a `:dev` alias, use `:[subrepo]/dev`. This makes it simple to merge multiple aliases and use them together from the root repository.
- Subrepos can only declare paths via alias `:extra-paths` and `:extra-deps`, not top-level `:paths` or `:deps`. These other paths are auto-included when starting a program, with a single repo that's desired, but in a monorepo, with multiple unified configs, that behavior is inflexible. It's better to explicitly load the subrepo aliases you need.
- Subrepos are only allowed to declare local deps that are inside root repo. If you need to load deps outside root repo, e.g. while development external libraries, you can configure that in your cross project deps file (typically found in `~/.clojure/deps.edn`).

`interdep.multi-repo/process` returns a map of: 
 - `::main-deps`, the unified deps config.
 - `::root-deps`, the root deps config.
 - `::subrepo-deps`, mapping of nested deps configs.

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

Then you'd call `interdep.multi-repo/process-deps` and the `::main-deps` output would be: 
```clj
{:aliases 
 {:app/main 
  {:extra-deps {rejoice-cljc/model {:local/root "../rejoice-model"}}}
  :api/main 
  {:extra-deps {rejoice-cljc/model {:local/root "../rejoice-model"}}}}}
```

With the processed deps config, you could call `clj -M:app/main:api/main` to run your project. Though listing out multiple namespaced aliases can get tedious, which is why the next namespace, `interdep.multi-alias`,  provides a better approach.

### Multiple Aliases

The `interdep.multi-alias` namespace is for matching multiple aliases based on configured profiles.

Instead of explicitly passing aliases, profiles let you filter out which ones not to include. This is useful when you have multiple subrepos with multiple aliases and you want to run several of those aliases together.

Usage: 
- Configure alias profiles in your root deps.edn using the `:interdep.multi-alias/profiles` config property.
- Call `interdep.multi-alias/with-profiles` to get matching alias configurations.

Profile options:
- `:path` - subrepo path to match aliases from. Defaults to matching from unified deps config.
- `:alias-ns*` - vector of alias key namespaces to match at least one of.
- `:alias-name*` - vector of alias key names to match at least one of.
- `:extra-opts` - extra options to include in context when profile is activated.

`interdep.multi-alias/profiles` returns map with:
- `::matched-aliases`, matched aliases based on passed profile keys and configured profile options.
- `::extra-options`, merged map of any `:extra-opts` in activated profiles.

As an example, a repo's `deps.edn` configs may be:
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

Then you could combine profiles depending on what aliases you wanted to use:
- With the `:dev` profile only, aliases `:app/main`, `:app/local`, `:api/main`, and `api:local` would be used.
- With the `:prod` profile only, aliases `:app/main` and `:api/main` would be used.
- With the `:app` and `:dev` combined, ony `:app/main` and `app/local` would be used.

