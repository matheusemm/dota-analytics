# DOTAlytics

REST API to ingest DOTA combat logs and extract match data.

## Goals

Create a simple REST API that provides the operations below and learn how to implement such API in Clojure.

* `POST /api/match`
Ingests a combat log text file, parses events and persists them. The combat log file can be posted using the following `curl` command:
```bash
curl -X POST "http://localhost:3000/api/match" -H  "accept: application/json" -H  "Content-Type: multipart/form-data" -F "combat-log-file=@combatlog_1.txt;type=text/plain"
```
* `GET /api/match/$match-id`
Returns the heroes in a match and the number of kills they made.
```json
[{
    "hero": "rubick",
    "kills": 7
}]
```
* `GET /api/match/$match-id/$hero-name/items`
Returns the items bought by a hero in a match and the moment they were bought. The timestamps (in milliseconds) are relative to the beginning of the match.
```json
[{
    "item": "quelling_blade",
    "timestamp": 530925
}]
```
* `GET /api/match/$match-id/$hero-name/spells`
Returns the spells cast by a hero and the number of times they were cast.
```json
[{
    "spell": "abyssal_underlord_firestorm",
    "casts": 83
}]
```
* `GET /api/match/$match-id/$hero-name/damage`
Returns the list of heroes damaged by the given hero, the number of times they were hit and total damage taken.
```json
[{
    "target": "snapfire",
    "damage-instances": 67,
    "total-damage": 79254
}]
```

## Implementation

### Stack

* Language: [Clojure](https://clojure.org/)
* Libraries:
  * [Ring](https://github.com/ring-clojure/ring): web application abstraction
  * [reitit](https://github.com/metosin/reitit): data-driven router
  * [malli](https://github.com/metosin/malli): data-driven schemas
  * [muuntaja](https://github.com/metosin/muuntaja): HTTP format negotiation, encoding and decoding
  * [Integrant](https://github.com/weavejester/integrant): micro-framework for building applications with data-driven architecture
* Database: [Datomic](https://www.datomic.com/)
* Tools:
  * [Hashp](https://github.com/weavejester/hashp): a better `prn` for debugging Clojure code
  * [Integrant-REPL](https://github.com/weavejester/integrant-repl): reloaded workflow with Integrant
  * [portal](https://github.com/djblue/portal): a Clojure tool to navigate throught your data

### reitit + malli + muuntaja

I found the integration between these libraries seamless. We just have to set the appropriate router data properties to configure reitit to use muuntaja and malli.

```clojure
(reitit.ring/router
 routes
 {:data {:coercion reitit.coercion.malli/coercion
         :muuntaja (muuntaja/create
                    (muuntaja/select-formats
                     muuntaja/default-options ["application/json"]))
         :middleware [reitit.ring.middleware.muuntaja/format-negotiate-middleware
                      reitit.ring.middleware.muuntaja/format-response-middleware
                      reitit.ring.middleware.muuntaja/format-request-middleware
                      reitit.ring.coercion/coerce-response-middleware
                      reitit.ring.coercion/coerce-request-middleware]}})
```

It took me some time to feel comfortable with reitit. It is simple in the surface but the integration with Ring adds more complexity. I had to read the docs a couple of times, try to write some code, and read it again before I could better understand the result, at runtime, of the configuration that I wrote.

One point that maybe could be improved is the default exception handler, `reitit.ring.middleware.exception/default-handler`. The response body returned by it is pretty much useless for debugging purposes during development. I changed mine to make use of hashp to print the exception:

```clojure
(defn default-exception-handler [^Exception e _]
  #p e
  {:status 500
   :body {:type "exception"
          :class (-> e .getClass .getName)
          :message (.getMessage e)}})
```

For development, maybe I could add the stacktrace to the body, that would make `#p` unecessary in this case.

I still have two questions about reitit that I want to investigate:
* Example codes that I saw place the `multipart-middleware` at the end of the middleware collection. For me it makes sense to place it after `parameters-middleware` and before the coercion middlewares so that any contribution made by the `multipart-middleware` in the request processing can be coerced. But maybe it doesn't matter because the multipart parameters will always have the same shape.
* AFAIK a pure Ring app + middlewares will result in `:params` in the request map being populated with the request parameters. With reitit it results in parameters inside `:parameters` and grouped by "type": `multipart`, `path`, etc. Is it a reitit feature? If yes, is it not problematic to "override" expected Ring behaviour?

### Integrant

[TODO]

### Hashp and Portal

Thanks [Kari Marttila](https://www.karimarttila.fi/) for [writing about Hashp and Portal](https://kari-marttila.medium.com/clojure-power-tools-part-1-fe97de6d445c), they are amazing!

Hashp is more convenient than `println` or `prn` because we just need to put a `#p` in front of the expression that we want to print, it is not necessary to wrap the expression around parenthesis, e.g. `(prn expression)`. Since I am no [Emacs](https://www.gnu.org/software/emacs/) keyboard wizard, adding/removing a prefix to an expression is easier than wrapping/unwrapping it. :-)

I found Portal super useful to investigate the request map. Reitit adds keys to the map, making it difficult to understand the result of a `(prn request)`. Portal can display data structures in different ways (coll, table, tree) and supports navigating the structures (via [datafy](https://clojuredocs.org/clojure.datafy/datafy) and [nav](https://clojuredocs.org/clojure.datafy/nav)). And because of Portal I was introduced to [tap](https://github.com/clojure/clojure/blob/master/changes.md#23-tap)!
