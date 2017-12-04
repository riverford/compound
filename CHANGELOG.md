# Change Log
## [Unreleased] 

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

[Unreleased]: https://github.com/riverford/compound/compare/0.5.0-alpha4....HEAD
[0.5.0-alpha4]: https://github.com/riverford/compound/compare/0.5.0-alpha3...0.5.0-alpha4
[0.5.0-alpha3]: https://github.com/riverford/compound/compare/0.5.0-alpha2...0.5.0-alpha3
[0.5.0-alpha2]: https://github.com/riverford/compound/compare/0.5.0-alpha1...0.5.0-alpha2
[0.5.0-alpha1]: https://github.com/riverford/compound/compare/0.4.0...0.5.0-alpha1

