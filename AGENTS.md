# AGENTS.md

## 1. Core Principle

You are working in an existing production codebase.

Your goal is to make the **smallest correct, clean, maintainable change** that fits the existing project.

Do not redesign the project unless the task explicitly requires it.

The existing codebase, architecture, conventions, and tests are the primary source of truth.

---

# 2. Mandatory Workflow

Before writing or modifying code:

1. Read this `AGENTS.md` completely.
2. Inspect the relevant project structure.
3. Inspect the existing implementation related to the task.
4. Inspect related tests.
5. Search for existing methods, utilities, services, repositories, validators, DTOs, models, and helpers that can be reused.
6. Understand how the existing code handles:

    * Errors
    * Validation
    * Database access
    * Logging
    * Authentication/authorization
    * Transactions
    * Responses
    * Testing
7. Plan the smallest appropriate change.
8. Implement the change.
9. Run relevant tests.
10. Fix implementation issues if tests fail.
11. Run the tests again.
12. Review the final diff.
13. Run linting/static analysis if available.
14. Only then consider the task complete.

Do not skip the inspection step just because the requested change appears simple.

---

# 3. Existing Architecture

Follow the architecture already established by the project.

When the project uses:

```text
Controller → Service → Repository
```

maintain that separation.

## Controller

Controllers should handle request/response concerns.

Controllers should generally:

* Receive requests.
* Parse request parameters/body according to existing conventions.
* Perform request-level validation if that is already the project's convention.
* Call the appropriate service.
* Return the appropriate response.

Controllers should NOT contain:

* Business logic.
* Complex data processing.
* Direct database queries.
* Large conditional business rules.

---

## Service

Services should contain business/application logic.

Services may:

* Validate business rules.
* Coordinate multiple repositories/services.
* Transform data when appropriate.
* Handle application-level workflows.

Services should NOT bypass repositories for database access when the project already uses repositories.

Avoid putting HTTP-specific logic into services unless the existing project explicitly follows that pattern.

---

## Repository

Repositories should handle data-access concerns.

Repositories may:

* Query the database.
* Insert/update/delete records.
* Encapsulate persistence logic.
* Handle database-specific operations.

Repositories should NOT contain business logic that belongs in services.

---

# 4. Reuse Existing Code

Before creating a new method, search the codebase for an existing implementation that can reasonably be reused.

Look for:

* Existing methods
* Existing services
* Existing repositories
* Existing utilities
* Existing validators
* Existing DTOs
* Existing models/entities
* Existing constants
* Existing exception classes
* Existing response helpers
* Existing test helpers

Prefer:

```text
reuse existing method
```

over:

```text
create another method with the same responsibility
```

Do not duplicate functionality.

Only create a new method when:

* No suitable method exists.
* The existing method has a different responsibility.
* Reusing it would make the code less clear.
* Reusing it would violate an architectural boundary.

When creating a new method, make its responsibility narrow and clear.

---

# 5. Clean Code

Code must be:

* Readable
* Simple
* Maintainable
* Consistent
* Testable
* Easy to understand

Prefer straightforward code over clever code.

Avoid:

* Unnecessary abstractions.
* Over-engineering.
* Duplicate logic.
* Dead code.
* Unused imports.
* Extremely long methods.
* Excessive nesting.
* Unnecessary comments.
* Magic values.
* Unnecessary temporary variables.
* Premature optimization.

Do not introduce a design pattern simply because it is theoretically applicable.

Use patterns already present in the project unless there is a clear reason to introduce something new.

---

# 6. Method Naming

Method names must clearly describe their purpose.

Prefer names such as:

```text
findUserByEmail()
calculateOrderTotal()
validatePaymentStatus()
updateProductInventory()
getActiveSubscriptions()
```

Avoid vague names such as:

```text
process()
handle()
execute()
doSomething()
getData()
```

unless the existing project already uses such naming consistently and the meaning is genuinely clear.

A developer should be able to understand the purpose of a method from its name without reading the implementation.

---

# 7. Naming Consistency

Follow the naming conventions already established in the project.

Do not introduce a different naming style.

Before creating a new:

* Method
* Class
* Variable
* DTO
* Repository
* Service
* Controller

inspect nearby existing code and follow its conventions.

Consistency is more important than personal preference.

---

# 8. Minimal Changes

Only modify what is necessary for the requested task.

Do NOT:

* Refactor unrelated code.
* Rename unrelated methods.
* Reorganize unrelated files.
* Upgrade dependencies without a clear reason.
* Rewrite working code unnecessarily.
* Change architecture unnecessarily.
* Change formatting across unrelated files.

If unrelated technical debt is discovered, mention it separately instead of modifying it.

---

# 9. Testing Rules

Testing is mandatory.

After every meaningful implementation change:

1. Run the relevant tests.
2. Inspect failures carefully.
3. Determine whether the failure is caused by:

    * Incorrect implementation
    * Incorrect assumption
    * Regression
    * Incorrect test expectation
    * Environment/setup issue
4. Fix the implementation when the implementation is wrong.
5. Run the tests again.

## Critical Rule

**NEVER modify a test simply to make the new implementation pass.**

Tests should represent expected behavior.

If:

```text
implementation → test fails
```

the default assumption should be:

```text
implementation is wrong
```

Fix the implementation first.

---

# 10. When Tests May Be Changed

Tests may only be changed when there is clear evidence that:

* The required behavior has intentionally changed.
* The existing test describes obsolete behavior.
* The existing test is objectively incorrect.
* The task explicitly requires changing the expected behavior.

If modifying a test is necessary:

1. Understand why the old expectation is no longer correct.
2. Preserve meaningful coverage.
3. Do not weaken assertions just to make the test pass.
4. Do not remove edge cases without justification.
5. Explain the reason for the test change.

Never turn:

```text
assert expected_result
```

into:

```text
assert result != null
```

just because the stronger assertion fails.

---

# 11. Test Real Behavior

Do not test only the happy path.

Where applicable, consider:

### Happy path

* Valid input
* Expected successful result
* Normal database state

### Invalid input

* Missing required values
* Invalid formats
* Invalid identifiers
* Empty values

### Boundary conditions

* Minimum values
* Maximum values
* Empty collections
* Large inputs
* Duplicate values

### Error handling

* Not found
* Unauthorized
* Forbidden
* Conflict
* Database errors
* External service failures

### Regression

Make sure existing behavior continues to work after the change.

---

# 12. Do Not Fake Test Success

Never:

* Mock away the behavior being tested just to make tests pass.
* Remove assertions.
* Skip failing tests without justification.
* Change expected values without understanding why.
* Add overly broad mocks that hide real problems.
* Catch exceptions merely to prevent tests from failing.

A passing test suite is only useful if it actually validates the intended behavior.

---

# 13. Error Handling

Follow the project's existing error-handling conventions.

Before creating a new exception or error response:

* Search for existing exception classes.
* Search for existing error-handling patterns.
* Reuse existing mechanisms where appropriate.

Do not introduce a new error-handling strategy for a single feature if the project already has an established one.

---

# 14. Database Access

Follow the existing database architecture.

If repositories are used:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Do not introduce direct database access from controllers or services when a repository already exists for that responsibility.

Reuse existing queries and repository methods whenever possible.

Before creating a new query:

1. Search existing repositories.
2. Check whether an existing query can be extended or reused.
3. Avoid duplicate database operations.

---

# 15. External Services

Follow existing patterns for:

* API clients
* HTTP requests
* Authentication
* Retry logic
* Timeouts
* Error handling
* Serialization
* Logging

Do not create a new client abstraction if an existing one already handles the same external service.

---

# 16. Comments

Code should generally explain itself through:

* Clear naming
* Small methods
* Simple logic
* Appropriate structure

Do not add comments that merely restate what the code does.

Bad:

```text
// Get the user
user = getUser(id)
```

Useful comments should explain:

* Why something is done.
* A non-obvious business rule.
* A necessary workaround.
* A constraint imposed by an external system.

---

# 17. Performance

Do not prematurely optimize.

First make the implementation:

1. Correct
2. Readable
3. Testable

Then consider performance when there is evidence that performance matters.

However, avoid obvious performance problems such as:

* N+1 database queries
* Repeated expensive operations inside loops
* Unnecessary database calls
* Loading significantly more data than necessary

When changing behavior for performance reasons, preserve correctness and test coverage.

---

# 18. Security

Follow existing security practices.

Never:

* Hardcode secrets.
* Commit API keys.
* Commit passwords.
* Log sensitive credentials.
* Bypass authentication/authorization.
* Disable security checks just to make tests pass.

Use the project's existing configuration and secret-management mechanisms.

---

# 19. Dependency Changes

Do not add a dependency unless it is actually necessary.

Before adding a dependency:

1. Check whether the project already has a library that provides the functionality.
2. Check whether the functionality can reasonably be implemented using existing dependencies.
3. Prefer existing project conventions.

Do not upgrade unrelated dependencies as part of a feature implementation.

---

# 20. Git Diff Discipline

Before finishing, review the final diff.

Look for:

* Unrelated changes
* Accidental formatting changes
* Debug statements
* Temporary code
* Unused imports
* Unnecessary files
* Modified tests
* Unexpected dependency changes

The final diff should be focused on the requested task.

---

# 21. Final Verification Checklist

Before declaring the task complete, verify:

* [ ] `AGENTS.md` rules were followed.
* [ ] Existing architecture was preserved.
* [ ] Controller/Service/Repository responsibilities are clear.
* [ ] Existing methods were reused where appropriate.
* [ ] No unnecessary duplicate methods were introduced.
* [ ] Method names clearly describe their purpose.
* [ ] Code is readable and maintainable.
* [ ] No unrelated refactoring was introduced.
* [ ] Relevant tests were executed.
* [ ] Edge cases were considered.
* [ ] Tests were not weakened just to make them pass.
* [ ] Implementation was fixed when tests exposed implementation bugs.
* [ ] Lint/static analysis was run if available.
* [ ] Final diff was reviewed.
* [ ] No secrets or sensitive information were introduced.

---

# 22. Decision Priority

When deciding how to implement something, follow this priority:

1. **Correctness**
2. **Existing project architecture**
3. **Existing project conventions**
4. **Reuse existing code**
5. **Testability**
6. **Readability**
7. **Maintainability**
8. **Minimal change**
9. **Performance optimization when justified**

Do not sacrifice correctness or architectural consistency for a shorter implementation.

---

# 23. Golden Rule

When uncertain:

**Inspect the existing code before inventing a new solution.**

Prefer:

```text
existing pattern
    ↓
reuse existing method
    ↓
minimal change
    ↓
test
    ↓
fix implementation
    ↓
test again
```

over:

```text
invent new architecture
    ↓
write lots of new code
    ↓
change tests until they pass
```

The objective is not to produce the most code.

The objective is to produce the **smallest correct change that naturally belongs in this codebase**.
