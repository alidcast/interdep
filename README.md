# Interdep

Interdep helps you intercept your project's `deps.edn` configuration(s) before starting your Clojure program, so that you can enhance the default [tools.deps](https://github.com/clojure/tools.deps.alpha) behavior for more complex use-cases.  

### Status 

Experimental. Example is working, but core usage is not yet complete.

### Usage

#### Core

The intended usage of Interdep is as follows:

1) Register plugins in in your project's root `deps.edn` file, under the `:interdep/config` property. 
2) Call `interdep.deps/process` to process these plugins sequentially.
3) The final deps output is returned and used basis for Clojure commands.

Steps 1) and 2) are done using Interdep. Step 3) can be scripted with tools like [Babashka](https://github.com/borkdude/babashka). See this library's [example](https://github.com/rejoice-cljc/interdep/tree/master/example) for reference.

#### Plugins

##### `interdep.plugins.multi-repo`

The main use-case for Interdep was working with monorepos. While tools.deps lets you configure local dependencies via `:local/root` property, it does not allow you to include sub-project aliases from the root project. Also, it's also not always possible to use `:local/root` dep paths in cases where deps need to be reproducible (e.g. when deploying Datomic Ions). So intercepting the configuration is a necessary workaround until tools.deps improves its multi-repo facilities.

Setup instructions:
- Make sure you have multi-repo plugin setup in your root deps.edn `:interdep/config` plugins property. 
- Register any nested repositories in your root deps.edn, using the `:interdep.multi-repo/registry` config property.
- Include any sub-repositories in another using the `interdep.multi-repo/includes` property.

For example, for this directory structure: 
```
- myapp-common
- myapp-frontend
- myapp-backend
deps.edn
```

Your root `deps.edn` config would be as follows:
```clj
{:interdep/config
 {:plugins [interdep.plugins.multi-repo]}

 :interdep.multi-repo/registry
 ["myapp-common"
  "myapp-frontend"
  "myapp-backend"]   
```

Then another sub-project could look like:

```clj
{:interdep.multi-repo/includes
 ["myapp-common"]}
```

Some considerations to keep in mind:
- Non-namespaced aliases are not allowed. This makes it easy to merge nested project aliases. So, for example, instead of a `:test` alias, use `:[sub-project]/test`.
- Sub-project top-level `:paths` or `:deps` are not allowed. These properties are auto-included when starting a program but that's less useful when calling them from a project where multiple dep configs are unified.

