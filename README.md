# Interdep

Interdep is a simple way to manage a monorepo (with *interdependent* projects) using Clojure's tools.deps. 

## Status

Alpha. Use as git lib.

## Motivation

With tools.deps alone, you can configure `:local/root` deps for each sub-project, but you can't activate their `aliases` from another project. So in practice, you have to configure all aliases in one place, defeating the purpose of separating logic into interdependent projects.

Interdep, as a result, provides a flexible yet standardized a way to unify multiple subrepos configs, update their paths, and activate their aliases from the from the root directory. It's as transparent as possible with tools.deps, so subrepos can be run independently as well.
## Design Goals 
* Preserve `:local/root` convention for configuring local dependencies.
* All subrepos to configure their deps in their own nested directory.
* Allow activating subrepo `aliases` from root directory.
* Provide a streamlined way to call multiple project aliases at once.
* Allow using as task runner or as standalone utility used alongside tools.deps.
 
## Documentation

This repo itself can be used as a reference for below instructions.

- [Configuration](#configuration)
  - [Multiple Subrepos](#multiple-subrepos)
  - [Multiple Aliases](#multiple-aliases)
- [Usage](#usage)
  - [Command Line Usage](#command-line-usage)
  - [Utility Usage](#utility-usage)
### Configuration

Configure your repository in the tools.deps `deps.edn` file.

#### Multiple Subrepos

The option `:interdep.multi-repo/registry` accepts a vector of subrepo paths to process into a unified config.

Example: 
```clj
{:interdep.multi-repo/registry
 ["apps/web" 
  "apps/mobile"
  "components/app"]
```

To make the multi-project setup work optimally, the following constraints are applied when Interdep processes those deps:
- Subrepos can only declare namespaced `aliases`, as this prevents conflict when merging multiple aliases together.
  - Example: instead of `:dev` use `:[subrepo]/dev`
- Subrepos can only declare local:root deps that are inside the root repo, as this simplifies path handling internally and makes your repository reproducible.
  -  If you need to load deps outside root repo, e.g. to load a development library, tools.deps loads a a local, cross-project file (typically found in `~/.clojure/deps.edn`) for these purposes.

Given these safeguards, here is an example of these subrepo configurations:

```clj
;; -- apps/web
{:paths ["src"]
 
 :deps {rejoice/app {:local/root "../components/app"}

 :aliases 
  {:web/test {:extra-paths ["test"]}}}   

;; -- apps/mobile
{:paths ["src"]
 
 :deps {rejoice/app {:local/root "../components/app"}

 :aliases 
 {:mobile/test 
  {:extra-paths ["test"]}}}

;; -- components/app
{:paths ["src"]

 :aliases 
 {:app/test 
  {:extra-paths ["test"]}}}
```

As shown above, Interdep does not impose any atypical configuration; only practical constraints for a sensible multi-repo setup. 

After interdep processes those deps, you could call the aliases as normal, or you could setup profiles, as described above, for matching multiple aliases at once.

#### Multiple Aliases

The option `:interdep.multi-alias/profiles` accepts a map of profile options for matching multiple aliases at once at startup.

Profile options:
- `:alias-ns*` - vector of alias key namespaces to match.
- `:alias-name*` - vector of alias key names to match.
- `:extra-opts` - extra options to include in context when profile is activated.


Example config:
```clj
;; root repo
 :interdep.multi-alias/profiles
 {:dev  {:alias-name* [:main :dev]
  :prod {:alias-name* [:main]}}

;; -- nested web repo
{:aliases 
 {:web/main {,,,}
  :web/dev {,,,}}}   

;; -- nested mobile repo
{:aliases 
 {:mobile/main {,,,}
  :mobile/dev {,,,}}
```

With the above config, calling the `:dev` profile would match `[:web/main :web/dev :mobile/main :mobile/dev]`.

### Usage 

Interdep can be used as a command line task for starting a Clojure program or as standalone utilities for processing subrepo deps.

#### Command Line Usage

You must call Interdep's task helper before starting a Clojure program. You can do so with any command like tool. The example below uses [Babashka](https://github.com/babashka/babashka).

```clj
{:deps {org.rejoice-cljc/interdep 
        {:git/url "https://github.com/rejoice-cljc/interdep.git"
         :sha "<latest>"}}

 :tasks
 {:requires ([interdep.cli :as interdep])
  run (clojure (interdep/cmd-args *command-line-args*))}}
```

Interdep processes its custom options and forwards any extra tools.deps arguments to the Clojure command. 

The following extra options are accepted:
* `-P` Match aliases based on configured profiles.

You could startup Clojure with a configured profile: 

```
bb start -P:dev
```

Or you could call the aliases directly:
```
bb start -A:web/main
```

The intention is to be as transparent with tools.deps existing command line options while adding a few extras to streamline multi-repo usage.

#### Utility Usage

You may use Interdep's utilities for processing deps configs directly. It'd be up to you to make them the basis for a Clojure program.

To input deps into a Clojure program you have two options: 
1) Include the processed deps as a Clojure cli `-Sdeps` argument.
2) Generate a deps.edn file and start Clojure from its directory.

You can reference the `interdep.task`, which uses the first alternative.

**`interdep.multi-repo/process-deps`** 

Helper to unify subrepo deps into a single config. 

Returns a map of: 
 - `::main-deps`, the unified root and nested deps config.
 - `::nested-deps`, the unified nested deps config.
 - `::root-deps`, the root deps config.
 - `::subrepo-deps`, mapping of registered subrepo deps configs.

Usage: `(multi-repo/process-deps)`

**`interdep.multi-alias/with-profiles`** 

Helper to get a list of matching aliases based on configured profiles.

Returns map of:
- `::matched-aliases`, matched aliases based on passed profile keys and configured profile options.
- `::extra-opts`, merged map of any `:extra-opts` in activated profiles.

Usage: `(multi-alas/with-profiles processed-deps profile-keys ?subrepo-path)`

## Development 

To run tests: 

```
clj -M:test -m kaocha.runner --watch

```
## License

Distributed under the [EPL v2.0](LICENSE)
