(ns scenarist.devcards.test
  (:require [devcards.core :as cards :include-macros true]
            [reagent.core :as r]))

(def counter (r/atom 0))

(defn counter-component [state]
  (fn []
    [:div
     [:span (str "counter: " @state)]
     [:button {:on-click (fn [_]
                           (println "swapping: " @state)
                           (swap! state inc))}
      "inc"]]))

(cards/defcard-rg counter-card
                  [counter-component counter]
                  counter
                  {:inspect-data true
                   :history      true})
