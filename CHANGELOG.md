# Change Log
## [2019.09.14]

### New Major Version
Compound2 is a total rewrite, and new major version.
It has a new (but very similar api).

- Macro implementation for faster performance
- Use of metadata extension gives a less noisy api
- Fewer extension points, but extension is easier
- Spec is no longer used, which can (if spec not used elsewhere) reduce the js bundle size by ~100k in cljs.

## [2018.01.26-1]
### Added
- Allow use of vector paths as well as single keywords as keys in primary and secondary indexes.

## [2018.01.24-1]
### Fixed
- Incorrect arity for `ex-info` in update-item

## [2017.12.20-1]
### Fixed
- Fixed broken spec for `compound.core/items`

## [0.5.0-alpha4] - 2017-12-04
### Changed
- Changed diffing keys - `add` -> `inserts`, `modify` -> `updates`, `remove` -> `deletes`.
  This is a) to avoid shadowing `clojure.core/remove` when destructuring, and b) because the plural names (inserts, updates, deletes) are closer to the operations
  you'd perform on a mutable target (e.g. a SQL database).

## [0.5.0-alpha3] - 2017-12-04
### Fixed
- Fixed arity exception on `assoc!` when `:keys` for an item is empty when adding or removing items from a `:compound/many-to-many` index

## [0.5.0-alpha2] - 2017-12-01
### Fixed
- Fixed arity exception on `assoc!` when removing items from a `:compound/one-to-many` index

## [0.5.0-alpha1] - 2017-12-01
### Added
- Added support for diffing compounds `(diff source target)` `(apply-diff compound diff)` - see https://riverford.github.io/compound/#diffing for docs
- Added `(primary-index-fn compound)`

## [0.4.0] - 2017-11-10

Initial public release

[Unreleased]: https://github.com/riverford/compound/compare/2018.01.26-1....HEAD
[2018.01.26-1]: https://github.com/riverford/compound/compare/2017.01.24-1...2018.01.26-1
[2018.01.24-1]: https://github.com/riverford/compound/compare/2017.12.20-1...2018.01.24-1
[2017.12.20-1]: https://github.com/riverford/compound/compare/0.5.0-alpha4...2017.12.20-1
[0.5.0-alpha4]: https://github.com/riverford/compound/compare/0.5.0-alpha3...0.5.0-alpha4
[0.5.0-alpha3]: https://github.com/riverford/compound/compare/0.5.0-alpha2...0.5.0-alpha3
[0.5.0-alpha2]: https://github.com/riverford/compound/compare/0.5.0-alpha1...0.5.0-alpha2
[0.5.0-alpha1]: https://github.com/riverford/compound/compare/0.4.0...0.5.0-alpha1
