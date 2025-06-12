(ns scenarist.ui.nvl
  (:require [reagent.core :as r]
            [scenarist.db :as db]
            [scenarist.engine.typewriter :as typewriter]
            [datascript.core :as d]))

(defn nvl-text-box
  "Компонент для отображения текста в NVL стиле"
  []
  (let [db @db/conn
        game-state (db/game-state db)
        current-scene-id (:db/id (db/current-scene db))
        lines (when current-scene-id 
                (db/scene-lines db current-scene-id))
        current-line (:game/current-line game-state)
        displayed-lines (take (inc current-line) lines)]
    [:div.nvl-container
     {:style {:position "absolute"
              :top "10%"
              :left "10%"
              :right "10%"
              :bottom "15%"
              :background-color "rgba(0, 0, 0, 0.7)"
              :padding "40px"
              :overflow-y "auto"
              :border-radius "10px"
              :box-shadow "0 4px 20px rgba(0, 0, 0, 0.5)"}}
     
     ;; Отображение всех предыдущих строк
     (for [[idx line] (map-indexed vector displayed-lines)]
       ^{:key idx}
       [:div.nvl-line
        {:style {:margin-bottom "20px"
                 :opacity (if (= idx current-line) 1 0.8)}}
        
        ;; Имя говорящего
        (when (:line/speaker line)
          [:div.speaker-name
           {:style {:color "#ffd700"
                    :font-weight "bold"
                    :margin-bottom "5px"
                    :font-size "18px"}}
           (:line/speaker line)])
        
        ;; Текст диалога
        [:div.dialogue-text
         {:style {:color "#ffffff"
                  :font-size "16px"
                  :line-height "1.6"}}
         (if (= idx current-line)
           (:game/displayed-text game-state)
           (:line/text line))]])]))

(defn background-layer
  "Слой с фоновым изображением"
  []
  (let [db @db/conn
        scene (db/current-scene db)
        bg-image (when scene 
                   (:scene/background (d/entity db scene)))]
    [:div.background
     {:style {:position "absolute"
              :top 0
              :left 0
              :width "100%"
              :height "100%"
              :background-image (when bg-image (str "url(" bg-image ")"))
              :background-size "cover"
              :background-position "center"
              :background-color "#1a1a1a"}}]))

(defn control-panel
  "Нижняя панель с кнопками управления"
  []
  (let [db @db/conn
        game-state (db/game-state db)]
    [:div.control-panel
     {:style {:position "absolute"
              :bottom 0
              :left 0
              :right 0
              :height "60px"
              :background-color "rgba(0, 0, 0, 0.8)"
              :display "flex"
              :align-items "center"
              :justify-content "center"
              :gap "20px"}}
     
     [:button.control-btn
      {:style {:padding "10px 20px"
               :background-color "#333"
               :color "#fff"
               :border "none"
               :border-radius "5px"
               :cursor "pointer"
               :transition "background-color 0.2s"}
       :on-mouse-over #(set! (.-style.backgroundColor (.-target %)) "#555")
       :on-mouse-out #(set! (.-style.backgroundColor (.-target %)) "#333")
       :on-click #(println "Auto mode")}
      "Авто"]
     
     [:button.control-btn
      {:style {:padding "10px 20px"
               :background-color "#333"
               :color "#fff"
               :border "none"
               :border-radius "5px"
               :cursor "pointer"
               :transition "background-color 0.2s"}
       :on-mouse-over #(set! (.-style.backgroundColor (.-target %)) "#555")
       :on-mouse-out #(set! (.-style.backgroundColor (.-target %)) "#333")
       :on-click (fn []
                   (let [current-speed (:game/text-speed game-state)
                         next-speed (case current-speed
                                      :slow :medium
                                      :medium :fast
                                      :fast :instant
                                      :instant :slow)]
                     (db/set-text-speed! next-speed)))}
      (str "Скорость: " (name (:game/text-speed game-state)))]
     
     [:button.control-btn
      {:style {:padding "10px 20px"
               :background-color "#333"
               :color "#fff"
               :border "none"
               :border-radius "5px"
               :cursor "pointer"
               :transition "background-color 0.2s"}
       :on-mouse-over #(set! (.-style.backgroundColor (.-target %)) "#555")
       :on-mouse-out #(set! (.-style.backgroundColor (.-target %)) "#333")
       :on-click #(println "History")}
      "История"]
     
     [:button.control-btn
      {:style {:padding "10px 20px"
               :background-color "#333"
               :color "#fff"
               :border "none"
               :border-radius "5px"
               :cursor "pointer"
               :transition "background-color 0.2s"}
       :on-mouse-over #(set! (.-style.backgroundColor (.-target %)) "#555")
       :on-mouse-out #(set! (.-style.backgroundColor (.-target %)) "#333")
       :on-click #(println "Menu")}
      "Меню"]]))

(defn handle-click!
  "Обработка клика по экрану"
  []
  (typewriter/handle-click!))

(defn nvl-game-screen
  "Основной экран игры"
  []
  [:div.game-screen
   {:style {:position "relative"
            :width "100vw"
            :height "100vh"
            :overflow "hidden"
            :background-color "#000"
            :cursor "pointer"
            :user-select "none"}
    :on-click handle-click!}
   [background-layer]
   [nvl-text-box]
   [control-panel]])