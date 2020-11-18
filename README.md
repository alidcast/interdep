# Interdep

Interdep helps you intercept your repo's `deps.edn` configuration(s) before starting your Clojure program, so that you can make the default functionality provided by Clojure's [tools.deps](https://github.com/clojure/tools.deps.alpha) work better for projects with many local subrepo dependencies.

**Why can't monorepos be managed with tools.deps alone?**

While tools.deps lets you configure local dependencies via `:local/root` property, it does not allow you to include sub-project aliases from the root project. So, for example, if a sub-project has a `:dev` alias, it is not called unless your starting the Clojure program from that project's directory. This is limiting, since you either have to declare all aliases in the root of the project (splitting the configuration of a nested sub-project) or call a command only from a sub-project's directory. Also, using local file path dependencies is not tenable if your deployment needs to be reproducible (such as when deploying Datomic Ions).

### Status

Experimental/Alpha. But the usage is working, and scope is small, so API is unlikely to change much.

### Usage

The intended usage of Interdep is as follows:

1) Process your project's `deps.edn` configuration(s).
2) Use the processed deps output as the basis for Clojure program commands.

Steps 1) is done using the Interdep namespaces below. Step 2) can be scripted in Bash, or with tools like [Babashka](https://github.com/borkdude/babashka). Just `spit` a deps.edn file into another directory and run your Clojure program from there. See this library's [example](https://github.com/rejoice-cljc/interdep/tree/master/example) for a working reference.

#### `interdep.multi-repo`

Usage: 
- Register any nested repositories in your root deps.edn, using the `:interdep.multi-repo/registry` config property.
- Call `interdep.multi-repo/process` to get deps config that combines your root and registered repository configurations.

Some considerations to keep in mind:
- Non-namespaced aliases are not allowed in sub-repos. So, for example, instead of having a `:dev` alias in a subrepo, you'd use `:[subrepo]/dev`. This makes it simple to merge multiple aliases and use them together in the root of a project.
- Top-level `:paths` or `:deps` are not allowed in sub-repos. These properties are meant to be auto-included when starting a program but that behavior is inflexible when multiple dep configs are unified.
- All `:extra-paths` and `:local/root` can be declared as usual. When processing deps, any path strings will be qualified to include their respective repository name, and can also be made relative to another directory using the `:root-dir` option. This was done so that you could still enter into a sub-project and start a Clojure program with its aliases, without any processing.

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
  {:extra-deps {org.rejoice/model {:local/root "../rejoice-model"}}}}}   

;; api deps.edn
{:aliases 
 {:api/main 
  {:extra-deps {org.rejoice/model {:local/root "../rejoice-model"}}}}}
```

Then you'd call `interdep.multi-repo/process` and the output would be: 
```clj
{:aliases 
 {:app/main 
  {:extra-deps {org.rejoice/model {:local/root "../rejoice-model"}}}
  :api/main 
  {:extra-deps {org.rejoice/model {:local/root "../rejoice-model"}}}}}
```

With that deps config, you could call `clj -M:app/main:api/main` to run your project. Though listing out multiple namespaced aliases can get tedious, which is why the next namespace, `interdep.multi-alias`,  provides a better approach.

##### `interdep.multi-alias` 

;; todo
