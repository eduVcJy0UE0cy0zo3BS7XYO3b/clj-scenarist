(ns scenarist.core
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom]
            [scenarist.db :as db]
            [scenarist.ui.nvl :as nvl]
            [scenarist.engine.typewriter :as typewriter]
            [scenarist.engine.script :as script]
            [datascript.core :as d]))

(defn add-demo-content! []
  ;; Добавляем демо-сцены через систему сценария
  (script/add-scene!
    {:id :intro
     :name "Пролог"
     :background "/images/backgrounds/placeholder.svg"
     :music "/audio/bgm/peaceful.mp3"
     :lines
     [{:text "Раннее утро. Солнечные лучи пробиваются сквозь занавески..."
       :speaker nil}
      {:text "Опять проспал! Нужно поторопиться в школу."
       :speaker "Главный герой"}
      {:text "Хотя... может, стоит прогулять первый урок?"
       :speaker "Главный герой"}]})
  
  (script/add-scene!
    {:id :school-gate
     :name "У школьных ворот"
     :background "/images/backgrounds/placeholder.svg"
     :music "/audio/bgm/school.mp3"
     :lines
     [{:text "Несмотря на сомнения, я всё-таки дошёл до школы."
       :speaker nil}
      {:text "О, ты всё-таки пришёл! Я думала, ты сегодня не появишься."
       :speaker "Одноклассница"}
      {:text "Конечно пришёл. Не могу же я пропустить контрольную по математике."
       :speaker "Главный герой"}
      {:text "Контрольная? Но она же была вчера!"
       :speaker "Одноклассница"}
      {:text "..."
       :speaker "Главный герой"}]})
  
  ;; Устанавливаем callback для перехода между сценами
  (typewriter/set-end-of-scene-callback! script/advance-scene!)
  
  ;; Переходим к первой сцене
  (script/jump-to-scene! :intro))

(defn app []
  [:div.app
   [nvl/nvl-game-screen]])

(defonce root (atom nil))

(defn init []
  (println "Initializing SCM Scenarist...")
  ;; Инициализируем состояние игры
  (db/init-game-state!)
  ;; Добавляем демо-контент
  (add-demo-content!)
  ;; Создаем root если его еще нет
  (when-not @root
    (reset! root (rdom/create-root (.getElementById js/document "app"))))
  ;; Рендерим приложение
  (.render @root (r/as-element [app])))

(defn ^:dev/after-load reload []
  (when @root
    (.render @root (r/as-element [app]))))