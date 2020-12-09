# Interdep

Interdep helps you intercept your repo's `deps.edn` configuration(s) before starting your Clojure program, so as to provide a sane, documented way of managing monorepo projects with Clojure's [tools.deps](https://github.com/clojure/tools.deps.alpha).

**Why can't monorepos be managed with tools.deps alone?**

While tools.deps lets you configure local dependencies via `:local/root` property, it does not allow you to include sub-project aliases from the root project. So, for example, if a subproject has a `:dev` alias, it is not called unless your starting the Clojure program from that project's directory. This is limiting, since you either have to declare all aliases in the root of the project or call a command only from the nested subproject. Lastly, using local file path dependencies is not tenable if your deployment needs to be reproducible (such as when deploying Datomic Ions).

## Status

Experimental/Alpha. But the usage is working, and scope is small, so API is unlikely to change much. Use git lib for now.

## Documentation

- [Usage](/docs/usage.md)

## License

Distributed under the [EPL v2.0](LICENSE)
