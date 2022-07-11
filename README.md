# hailtechno

Personal web server for music upload and streaming. I wanted somewhere to upload my music without paying the 
SoundCloud subscription.

## Profiles

Add a `profiles.clj` file:

```
{:dev  {:env {:db-host "localhost"
              :db-name "hailtechno"
              :db-user "postgres"}}
 :test {:env {:db-host "localhost"
              :db-name "hailtechno-test"
              :db-user "postgres}}}
```

### Setup

Install and run `postgres` and create a database called `hailtechno`

### Run

With [leiningen](https://leiningen.org/) installed, run
```
DB_USER=postgres \ 
DB_PASS=password \
DB_NAME=hailtechno \
lein run
```

Server will start on port `3000`

## Usage

    $ java -jar hailtechno-0.1.0-standalone.jar [args]