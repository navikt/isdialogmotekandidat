![Build status](https://github.com/navikt/isdialogmotekandidat/workflows/main/badge.svg?branch=master)

# isdialogmotekandidat

## Technologies used

* Docker
* Gradle
* Kotlin
* Ktor
* Postgres

##### Test Libraries:

* Kluent
* Mockk
* Spek

#### Requirements

* JDK 11

### Build

Run `./gradlew clean shadowJar`

### Lint (Ktlint)
##### Command line
Run checking: `./gradlew --continue ktlintCheck`

Run formatting: `./gradlew ktlintFormat`
##### Git Hooks
Apply checking: `./gradlew addKtlintCheckGitPreCommitHook`

Apply formatting: `./gradlew addKtlintFormatGitPreCommitHook`

This application consumes the following topic(s):

* teamsykefravr.isoppfolgingstilfelle-oppfolgingstilfelle-person

## Contact

### For NAV employees

We are available at the Slack channel `#isyfo`.
