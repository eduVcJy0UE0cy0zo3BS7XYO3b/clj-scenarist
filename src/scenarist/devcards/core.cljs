(ns scenarist.devcards.core
  (:require
   ["highlight.js" :as hljs]
   ["highlight.js/lib/languages/clojure" :as clojure-highlighting]
   ["marked" :refer [marked]]
   [devcards.core :as cards :include-macros true]
   [scenarist.devcards.test]))

;; https://github.com/bhauman/devcards/blob/master/src/devcards/util/markdown.cljs#L28
(js/goog.exportSymbol "DevcardsSyntaxHighlighter" hljs)
(js/goog.exportSymbol "DevcardsMarked" marked)

(hljs/registerLanguage "clojure" clojure-highlighting)

(defn ^:export init []
  (devcards.core/start-devcard-ui!))
