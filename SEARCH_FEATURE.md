# Search Feature Plan

## Scope
Plan the next `/view` workspace enhancement: clicking a column value in a result row should run a composed search filtered by that value across every dataset where that column is a key.

Example:

- clicking `PAT1` in `claims.patientId` should open a composed result that includes:
  - `patients` filtered by `patientId:=PAT1`
  - `visits` filtered by `patientId:=PAT1`
  - `prescriptions` filtered by `patientId:=PAT1`
  - `claims` filtered by `patientId:=PAT1`
  - `claim-review` filtered by `patientId:=PAT1`

This is not a small rendering tweak. The current implementation is single-dataset end to end, so composed search needs explicit model, service, and rendering changes.

## Current Baseline
Already implemented in `/view`:

- one selected dataset per request
- compact query parsing with flags, contains, wildcard, exact, and regex filters
- sorting, grouping, pagination, saved searches, export, and inline row editing
- dataset metadata defined in `ViewSearchService`
- HTML rendering handled entirely in `HospitalClaimsHttpServer`

Current one-dataset assumptions:

- `ViewQuery` stores exactly one `ViewDataset`
- `ViewSearchResult` stores exactly one dataset result set
- `handleView()` calls `viewSearchService.search(...)` once
- `renderViewResults()` renders one field catalog, one summary, and one table/group block
- exports and saved searches persist a single dataset/query combination

## Product Behavior
### User Flow
1. Operator runs a normal `/view` search.
2. Any cell whose column is marked as a composable key is rendered as a link.
3. Clicking the link opens `/view` in "composed search" mode.
4. The page shows one result section per matching dataset, all filtered by the clicked key/value pair.
5. The operator can still refine, export, or save that composed search.

### Initial Composable Keys
Start with keys that are already present across datasets and have clear semantics:

- `patientId`
- `doctorId`
- `drugId`
- `prescriptionId`
- `claimId`
- `insuranceId`

Recommended exclusions for the first pass:

- `fullName`, `doctorName`, `drugName`: labels, not stable keys
- `dateOfVisit`: part of the visit identity, but not useful as a global composed-search pivot by itself
- flags such as `eligible`: state filters, not keys

### Dataset Coverage By Key
The key registry should encode which datasets participate for each key.

Recommended initial mapping:

- `patientId` -> `patients`, `visits`, `prescriptions`, `claims`, `claim-review`
- `doctorId` -> `doctors`, `visits`, `prescriptions`, `claim-review`
- `drugId` -> `drugs`, `prescriptions`, `claim-review`
- `prescriptionId` -> `prescriptions`, `claims`, `claim-review`
- `claimId` -> `claims`
- `insuranceId` -> `patients`, `claims`

Notes:

- `patients.insuranceId` is a foreign key, not the patient row key, but it still fits the requested "every dataset in which it is a key" behavior because the column meaning is stable across datasets.
- A dataset should only participate if the column has the same identifier meaning, not merely the same string shape.

## Architecture Changes
### 1. Add Explicit Key Metadata
The current `ViewColumn` only has `key` and `label`. That is not enough to decide whether a cell should become a composed-search link.

Extend dataset metadata so each column can describe:

- whether the column is clickable for composed search
- which logical key family it belongs to
- whether the value should be matched exactly

Recommended additions:

- extend `ViewColumn` with optional composed-search metadata, or
- introduce a richer `ViewColumnDefinition` and keep `ViewColumn` as a rendering DTO

Suggested metadata fields:

- `key`
- `label`
- `composable`
- `keyFamily`
- `matchMode` with an initial default of exact match

This metadata should live next to dataset definitions in `ViewSearchService`, because that is already the authoritative field catalog.

### 2. Introduce A Key Registry
Add one authoritative registry that answers:

- given a clicked column, is it composable?
- if so, which logical key family does it represent?
- which datasets participate for that key family?
- which column name should be filtered in each dataset?

Suggested type:

- `ViewKeyFamily` enum for `PATIENT_ID`, `DOCTOR_ID`, `DRUG_ID`, `PRESCRIPTION_ID`, `CLAIM_ID`, `INSURANCE_ID`
- `ComposedSearchPlan` or `ViewComposedSearchDefinition`

This avoids scattering switch statements across the HTTP renderer and the search service.

### 3. Split Single-Dataset Search From Composed Search
Keep the existing `search(...)` path intact for ordinary requests.

Add a second service entry point for composed search, for example:

- `searchComposed(String sourceDatasetKey, String sourceColumnKey, String value, ...)`

That method should:

1. validate that the source column is composable
2. resolve the logical key family
3. build one exact-match child query per participating dataset
4. execute each child query through the existing single-dataset pipeline
5. aggregate the child results into a composed response object

Recommended new types:

- `ViewComposedQuery`
- `ViewDatasetSearchResult` or reuse `ViewSearchResult` as the per-dataset leaf
- `ViewComposedSearchResult`

This preserves the current tested search semantics while adding a higher-level orchestration layer.

### 4. Preserve Existing Query Semantics
Composed searches should use exact matching by default:

- clicking `PAT1` should translate to `patientId:=PAT1`, not `patientId:PAT1`

Reasoning:

- key clicks are navigational, not fuzzy search input
- exact matching avoids accidental expansion when identifiers are prefix-like

The single-dataset parser does not need a new filter operator if the composed layer emits exact-match child queries using the existing `:=` syntax internally.

## HTTP And Rendering Changes
### Request Shape
`/view` needs to support both ordinary and composed searches.

Recommended query parameters:

- ordinary mode:
  - `dataset`
  - `query`
  - `sort`
  - `group`
  - `groupSort`
  - `page`
  - `pageSize`
- composed mode:
  - `composed=on`
  - `sourceDataset`
  - `sourceColumn`
  - `value`

Optional later parameter:

- `datasets=patients,visits,...` to let operators narrow the composed target set

### Handler Changes
`handleView()` should branch early:

- if `composed` is absent, use the current single-dataset flow
- if `composed` is present, call the new composed-search service and render a composed result page

Avoid overloading `dataset` in composed mode. The page should still know the source dataset for context, but the result is no longer one selected dataset.

### Result Rendering
Add a dedicated composed-results renderer instead of stretching `renderViewResults()` until it handles two unrelated modes.

Recommended structure:

- top summary card:
  - clicked key
  - source dataset and source column
  - number of datasets searched
  - total records across sections
- one section per dataset:
  - dataset name
  - record count
  - table rendering using that dataset's existing columns

Recommended ordering:

1. source dataset first
2. then remaining datasets in the same order as `ViewDataset.values()`

### Clickable Cells
In `renderViewRecordTable()`:

- if a column is composable and the cell has a non-blank value, render it as a link to `/view?composed=on&sourceDataset=...&sourceColumn=...&value=...`
- otherwise render plain text as today

Do not make every cell clickable. The behavior should be limited to columns that the metadata explicitly marks as composed-search pivots.

### Field Catalog
The field catalog should indicate which columns support composed search.

Recommended UI addition:

- a third field-catalog column such as `Composed search`
- values like `Key link` or blank

This makes the interaction discoverable without extra documentation.

## Saved Searches And Export
### Saved Searches
Current saved searches persist one dataset and one query string. That does not naturally represent composed searches.

Recommended approach:

- preserve the existing format for single-dataset searches
- extend `SavedViewSearch` with an optional search mode and composed-search fields

Suggested new fields:

- `mode` with values `single` or `composed`
- `sourceDataset`
- `sourceColumn`
- `value`

This is a breaking change for the properties serialization logic unless handled carefully. The store should remain backward-compatible with existing saved searches.

### Export
The current export endpoint returns one CSV or one flat JSON payload.

For composed search:

- CSV should probably be deferred in the first pass, because multiple dataset schemas do not fit one flat file cleanly
- JSON is straightforward and should return a top-level object with one array per dataset section

Recommended first-pass behavior:

- keep current exports for single-dataset searches
- add composed-search JSON export
- return a validation message if composed-search CSV is requested

If CSV is required later, prefer a ZIP of per-dataset CSV files instead of a lossy merged sheet.

## Inline Editing
Inline editing should remain dataset-local.

Composed-search pages can still render inline edit controls inside editable dataset sections, but the redirect-back logic must preserve composed-search state instead of the current single-dataset query state.

That means the current `hiddenViewStateFields()` and `viewStateFromForm()` helpers need to support both:

- single-dataset search state
- composed-search state

Recommended approach:

- introduce a small `ViewPageState` abstraction rather than adding more ad hoc hidden fields

## Testing Plan
### Service Tests
Add tests for:

- key-family resolution from dataset column metadata
- exact-match child-query generation for composed searches
- correct participating datasets for each key family
- empty composed sections when a dataset has no matches
- rejection of non-composable columns

### HTTP Tests
Add tests for:

- clickable links rendered on composable key cells
- composed-search page rendering across multiple datasets
- source dataset rendered first
- inline validation for invalid composed-search requests
- backward compatibility for ordinary `/view` searches

### Persistence Tests
Add tests for:

- saving and reloading a composed search
- backward-compatible loading of old saved-search records

### Export Tests
Add tests for:

- composed JSON export shape
- rejection or deferral message for composed CSV export

## Implementation Sequence
1. Add richer column metadata and a key-family registry.
2. Implement composed-search service models and orchestration using the existing single-dataset search path as the leaf executor.
3. Add composed-mode request parsing and dedicated rendering in `HospitalClaimsHttpServer`.
4. Render clickable key cells in dataset tables.
5. Extend saved-search persistence for composed mode with backward compatibility.
6. Add composed JSON export and explicitly reject composed CSV until a multi-file export format is chosen.
7. Expand tests across service, HTTP, persistence, and export flows.

## Risks
- The current `/view` code mixes query state, rendering, persistence, and inline-edit return-state assumptions around one dataset. Composed search touches all of them.
- Export and saved-search formats become more complex once the page can represent multiple datasets.
- If composed rendering is forced into `ViewQuery` and `ViewSearchResult`, the single-dataset path will become harder to reason about. Separate composed types are safer.

## Recommendation
Implement composed search as a second, explicit `/view` mode layered on top of the existing single-dataset search engine.

Do not try to fake this by concatenating datasets into one synthetic table. The datasets have different schemas, different editability rules, different export needs, and different summaries. A composed page with one section per dataset matches the product request and fits the current architecture with less distortion.
