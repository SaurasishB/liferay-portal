# Integration Test Compile

Compiles the integration tests that consume a changed module, without running them. Liferay keeps those tests in a sibling `-test` module, so the module compiled is rarely the one the branch changed.

## Match

`^modules/.+\.java$`

## Command

Take the changed modules:

```bash
bash "${SKILL_DIR}/select_paths.sh" "${MERGE_BASE}" "${VALIDATION_FILE}" \
	| bash "${SKILL_DIR}/find_modules.sh" "${MERGE_BASE}" \
	| cut -d " " -f1 \
	| command grep --invert-match '^-$' \
	| sort --unique
```

Leave out `modules/dxp/apps/saml/saml-admin-rest-test` and every module under `modules/sdk`. When the runner says Full Portal Build is in the run, compile nothing and report **NOT VERIFIED**, naming Full Portal Build as the validation this one defers to.

An affected module is one that holds `testIntegration` sources compiled against the change, which is almost never the module the diff changed. Liferay keeps integration tests in a sibling `-test` module, so `apps:blogs:blogs-api` is covered by `apps:blogs:blogs-test` rather than by itself. Scoping this to changed directories compiles `NO-SOURCE` and establishes nothing.

For each changed module, take every module under the same parent directory whose name ends in `-test` and which has a `src/testIntegration` tree. Add any `-test` module whose `build.gradle` declares the changed module with `project(":<path>")`, where `<path>` is the changed module's Gradle project path. Find those modules by reading the index:

```bash
(cd "${REPO_ROOT}" && git grep --cached --files-with-matches --fixed-strings 'project(":<path>")' -- '*.gradle') \
	| bash "${SKILL_DIR}/find_modules.sh" "${MERGE_BASE}" \
	| cut -d " " -f1
```

Cap the set at 8 and name the full count when the cap binds.

Take the Gradle project path of each module in the set:

```bash
printf '%s\n' <module directory>... | sed "s#^modules/##; s#/#:#g"
```

Then compile it:

```bash
("${REPO_ROOT}/gradlew" \
	--parallel \
	--project-dir "${REPO_ROOT}/modules" \
	:<path>:compileTestIntegrationJava \
	--rerun)
```

Keep `--rerun`, which is an option on the compile task and so follows the task path. Without it this validation reports a green log for a branch it never compiled. A warm tree prints `compileTestIntegrationJava UP-TO-DATE` against outputs older than the branch sources, and clearing the output directory alone only downgrades that to `FROM-CACHE`. Read the task line before the build result, and treat a `BUILD SUCCESSFUL` whose compile task did not execute as no evidence at all.

FAIL when a compile reports `BUILD FAILED` on an error naming something the diff changed, and report the module and the error.

Every other failure belongs to someone else. These modules are ones the branch did not touch, so an error in a file the diff never changed is breakage that predates it. Confirm that rather than assuming it, and run the control **first**, since when it fails there is nothing to learn from compiling the affected set at all.

Pick a control that has no dependency on anything the diff touched **and whose `testIntegration` sources import the same package the failure named**. When it fails the same way, the breakage is the environment's: report **NOT VERIFIED** naming the error, and do not fail the branch.

A control that passes proves nothing on its own and never licenses a FAIL. Most `-test` modules import `com.liferay.portal.kernel` or `com.liferay.petra` and fail on a broken snapshot, but a few import neither and compile clean on any branch, so an unlucky pick returns `BUILD SUCCESSFUL` while the environment is thoroughly broken. When the control passes, fall back to the rule above: FAIL only when an error names something the diff changed, and otherwise report **NOT VERIFIED**.

One shape worth knowing is an error naming a package that plainly exists in the tree, such as `package com.liferay.portal.kernel.model does not exist`. `install-portal-snapshots` installs poms whose dependency versions are unsubstituted build tokens, so `portal-impl` resolves carrying nothing and the kernel is absent from `testIntegrationCompileClasspath`. That absence does not on its own break a compile, and a tree in exactly that state has compiled a set of `-test` modules clean, so the kernel reaches them by some path the resolved configuration does not show. Treat this as one possibility the control settles rather than the explanation to reach for, since assuming it turns a real failure into an environmental one.

An affected set that came out empty means the changed modules have no integration tests, so report **NOT VERIFIED** and name them as uncovered. PASS when every module in the set reports `BUILD SUCCESSFUL`.

## Checklist

Add one subitem per affected module:

```
- [ ] Compile testIntegration: <module path>
```

## Time Estimate

~30 sec per module (parallelized), plus a single control compile.