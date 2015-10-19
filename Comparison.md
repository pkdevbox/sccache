# Comparison Chart #
| | **Shop.com Cache** | **Memcached** | **Jboss** | **cache4j** | **EHCache** | **JCS** |
|:|:-------------------|:--------------|:----------|:------------|:------------|:--------|
|Non-Transactional| **+Y+**            | **+Y+**       | O         | **+Y+**     | ?           | **+Y+** |
|Any Size Key| **+Y+**            | N             | ?         | ?           | P           | P       |
|Any Size Data| **+Y+**            | N             | ?         | ?           | P           | P       |
|Reg Ex Key Matching| **+Y+**            | N             | ?         | ?           | N           | **+Y+** |
|Iterate Keys| **+Y+**            | N             | NL        | N           | N           | NL      |
|Data stored to disk| **+Y+**            | N             | **+Y+**   | N           | **+Y+**     | **+Y+** |
|Associative Keys| **+Y+**            | N             | N         | N           | N           | N       |
|Auto-GC based on TTL| **+Y+**            | Y (note 1)    | **+Y+**   | **+Y+**     | **+Y+**     | **+Y+** |
|100% Java| **+Y+**            | N             | P         | **+Y+**     | **+Y+**     | **+Y+** |
|Pluggable Architecture| **+Y+**            | N             | **+Y+**   | N           | **+Y+**     | **+Y+** |
|In-process cache| **+Y+**            | **+Y+**       | **+Y+**   | **+Y+**     | **+Y+**     | **+Y+** |
|External, Shared Cache| **+Y+**            | N             | **+Y+**   | N           | **+Y+**     | **+Y+** |
|Unlimited Objects in Cache| **+Y+**            | ?             | P         | ?           | N           | N (note 2) |
|Container/Platform Neutral| **+Y+**            | **+Y+**       | **+Y+**   | **+Y+**     | **+Y+**     | **+Y+** |
|Horizontally Scalable| **+Y+**            | **+Y+**       | **+Y+**   | N           | **+Y+**     | **+Y+** |
|Simple/Clean API| **+Y+**            | **+Y+**       | N         | **+Y+**     | N           | Y (note 3) |
|Self-contained - No third party libraries| **+Y+**            | **+Y+**       | N         | **+Y+**     | N           | N       |
|No XML| **+Y+**            | **+Y+**       | N         | N           | N           | **+Y+** |
|Cache any Serializable object| **+Y+**            | **+Y+**       | **+Y+**   | **+Y+**     | **+Y+**     | **+Y+** |
|License| Apache             | BSD           | LGPL      | BSD         | Apache      | Apache  |

  * +Y+ - Yes
  * N - No
  * O - Optional
  * ? - I don't know
  * NL - I don't see any APIs/support in their doc
  * P - Probably / it seems so

Note 1: It appears that memcached requires you to request a given key before it will expire it. This is not optimum as old keys that are never referenced again will bloat the cache.

Note 2: JCS's config specifies MaxObjects. I assume, therefore, that the number of objects is fixed.

Note 3: The basic JCS API seems simple enough. But, there are tons of parameters, options, etc. Some may think this is good, it would overwhelm me.

---

I've built this chart by searching the web and reading FAQs and manuals. Please let me know ASAP about any mistakes and I'll update the chart.