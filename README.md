# Interdep

Interdep is a flexible way to manage a monorepo (with *interdependent* projects) using deps.edn. 

## Status

Alpha. Use as git lib.

## Motivation

With tools.deps alone, you can configure `:local/root` deps for each sub-project, but you can't activate their `aliases` from another project. So in practice, you have to configure all aliases in one place, defeating the purpose of separating logic into interdependent projects.

The goal of Interdep, then, is to provide a way to load multiple sub-project aliases from the root project, while still being able to run the subrepos independently when necessary.

## Design Goals 

* Provide simple utilities that can be used alongside tools.deps. 
* Preserve `:local/root` convention for configuring local dependencies.
* Allow activating `aliases` from root directory.
* Provide a streamlined way to call multiple project aliases at once.

## Documentation

- [Usage](/docs/usage.md)

## License

Distributed under the [EPL v2.0](LICENSE)
