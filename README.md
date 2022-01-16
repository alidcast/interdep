# Interdep

Interdep is a flexible way to manage monorepo projects using deps.edn. 

## Motivation

With tools.deps alone, you can configure `:local/root` deps for each sub-project, but you can't activate their aliases. So in practice, you have to configure all aliases in the root of the project, defeating the purpose of trying to separate logic into independent subrepos.

With Interdep, instead, you can process nested subrepo deps and turn them into a unified config that you call from your root directory, while still being able to run the subrepos independently when necessary.

## Status

Alpha. Use as git lib.

## Documentation

- [Usage](/docs/usage.md)

## License

Distributed under the [EPL v2.0](LICENSE)
