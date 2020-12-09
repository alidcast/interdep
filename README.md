# Interdep

Interdep helps you intercept your repo's `deps.edn` configuration(s) before starting your Clojure program, so as to provide a sane, documented way of managing monorepo projects using tools.deps.

## Motivation

You have a project with multiple, independent sub-projects nested inside. With tools.deps alone, you can configure the appropriate `:local/root` deps for each sub-project, but you can't activate their aliases. So in practice, you'd end up configuring all aliases in the root of the project, defeating the purpose of trying to separate logic into independent sub-projects.

With interdep, you have: 

1) A standardized way of [unifying multipe dep configs](/docs/usage.md#configuring-multiple-surepos), so that you can use them as inputs when starting your Clojure program. 
2) A way of [matching multiple aliases based on configured profiles](/docs/usage.md#configuring-multiple-aliases), since calling aliases one-by-one can get tedious as your project grows.

## Status

Experimental/Alpha. But the usage is working, and scope is small, so API is unlikely to change much. Use git lib for now.

## Documentation

- [Usage](/docs/usage.md)

## License

Distributed under the [EPL v2.0](LICENSE)
