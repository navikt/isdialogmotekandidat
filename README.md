![Build status](https://github.com/navikt/isdialogmotekandidat/workflows/main/badge.svg?branch=master)

# isdialogmotekandidat

## Technologies used

* Docker
* Gradle
* Kotlin
* Ktor
* Postgres
* Kafka

##### Test Libraries:

* Kluent
* Mockk
* Spek

#### Requirements

* JDK 11

## Download packages from Github Package Registry

Certain packages (isdialogmote-schema) must be downloaded from Github Package Registry, which requires
authentication. The packages can be downloaded via build.gradle:

```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/navikt/isdialogmote-schema")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}
```

`githubUser` and `githubPassword` are properties that are set in `~/.gradle/gradle.properties`:

```
githubUser=x-access-token
githubPassword=<token>
```

Where `<token>` is a personal access token with scope `read:packages`(and SSO enabled).

The variables can alternatively be configured as environment variables or used in the command lines:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

### Build

Run `./gradlew clean shadowJar`

### Lint (Ktlint)
##### Command line
Run checking: `./gradlew --continue ktlintCheck`

Run formatting: `./gradlew ktlintFormat`
##### Git Hooks
Apply checking: `./gradlew addKtlintCheckGitPreCommitHook`

Apply formatting: `./gradlew addKtlintFormatGitPreCommitHook`

This application produces the following topic(s):

* teamsykefravr.isdialogmotekandidat-dialogmotekandidat
* teamsykefravr.isdialogmotekandidat-dialogmotekandidat-midlertidig (Topic is created for temporary use, until topic `teamsykefravr.isdialogmotekandidat-dialogmotekandidat` is available in Production.

This application consumes the following topic(s):

* teamsykefravr.isoppfolgingstilfelle-oppfolgingstilfelle-person
* teamsykefravr.isdialogmote-dialogmote-statusendring

## Contact

### For NAV employees

We are available at the Slack channel `#isyfo`.
