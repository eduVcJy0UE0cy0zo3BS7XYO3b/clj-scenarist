# Фаза 1: Базовый движок визуальных новелл

## Цель первой фазы
Создать минимально жизнеспособный продукт (MVP) - работающий движок визуальных новелл в NVL стиле, который позволит:
- Читать текст истории с посимвольным появлением
- Переходить между сценами
- Отображать фоновые изображения
- Управлять скоростью текста
- Загружать сценарии из файла

## 1. Схема DataScript

### 1.1 Определение схемы базы данных
```clojure
;; src/scenarist/db.cljs
(ns scenarist.db
  (:require [datascript.core :as d]))

(def schema
  {;; Сцена - основная единица повествования
   :scene/id {:db/unique :db.unique/identity}
   :scene/name {:db/type :db.type/string}
   :scene/background {:db/type :db.type/string}
   :scene/music {:db/type :db.type/string}
   :scene/lines {:db/valueType :db.type/ref
                 :db/cardinality :db.cardinality/many}
   
   ;; Строка диалога
   :line/id {:db/unique :db.unique/identity}
   :line/text {:db/type :db.type/string}
   :line/speaker {:db/type :db.type/string}
   :line/position {:db/type :db.type/long}
   
   ;; Состояние игры
   :game/current-scene {:db/type :db.type/ref}
   :game/current-line {:db/type :db.type/long}
   :game/text-speed {:db/type :db.type/keyword}
   :game/displayed-text {:db/type :db.type/string}
   :game/is-typing {:db/type :db.type/boolean}})

(defonce conn (d/create-conn schema))

;; Инициализация начального состояния
(defn init-game-state! []
  (d/transact! conn
    [{:db/id -1
      :game/current-line 0
      :game/text-speed :medium
      :game/displayed-text ""
      :game/is-typing false}]))
```

### 1.2 Примеры запросов
```clojure
;; Получить текущую сцену
(defn current-scene [db]
  (d/q '[:find ?scene .
         :where [_ :game/current-scene ?scene]]
       db))

;; Получить все строки текущей сцены
(defn scene-lines [db scene-id]
  (d/q '[:find [(pull ?line [:line/text :line/speaker :line/position]) ...]
         :in $ ?scene
         :where 
         [?scene :scene/lines ?line]]
       db scene-id))

;; Получить текущее состояние игры
(defn game-state [db]
  (d/q '[:find (pull ?e [:game/current-line :game/text-speed :game/displayed-text]) .
         :where [?e :game/current-line]]
       db))
```

## 2. NVL компонент

### 2.1 Основной компонент отображения
```clojure
;; src/scenarist/ui/nvl.cljs
(ns scenarist.ui.nvl
  (:require [reagent.core :as r]
            [scenarist.db :as db]
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
        bg-image (:scene/background scene)]
    [:div.background
     {:style {:position "absolute"
              :top 0
              :left 0
              :width "100%"
              :height "100%"
              :background-image (when bg-image (str "url(" bg-image ")"))
              :background-size "cover"
              :background-position "center"}}]))

(defn nvl-game-screen
  "Основной экран игры"
  []
  [:div.game-screen
   {:style {:position "relative"
            :width "100vw"
            :height "100vh"
            :overflow "hidden"
            :background-color "#000"}}
   [background-layer]
   [nvl-text-box]
   [control-panel]])
```

### 2.2 Панель управления
```clojure
(defn control-panel
  "Нижняя панель с кнопками управления"
  []
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
             :cursor "pointer"}
     :on-click #(db/toggle-auto-mode!)}
    "Авто"]
   
   [:button.control-btn
    {:on-click #(db/change-text-speed!)}
    "Скорость"]
   
   [:button.control-btn
    {:on-click #(db/show-history!)}
    "История"]
   
   [:button.control-btn
    {:on-click #(db/show-menu!)}
    "Меню"]])
```

## 3. Система посимвольного появления текста

### 3.1 Typewriter эффект
```clojure
;; src/scenarist/engine/typewriter.cljs
(ns scenarist.engine.typewriter
  (:require [datascript.core :as d]
            [scenarist.db :as db]))

(def speed-map
  {:slow 100
   :medium 50
   :fast 20
   :instant 0})

(defn start-typing!
  "Начинает посимвольный вывод текста"
  [text]
  (let [speed (-> @db/conn
                  db/game-state
                  :game/text-speed
                  speed-map)]
    (d/transact! db/conn
      [{:db/id [:game/current-line]
        :game/displayed-text ""
        :game/is-typing true}])
    
    (if (= speed 0)
      ;; Мгновенное отображение
      (d/transact! db/conn
        [{:db/id [:game/current-line]
          :game/displayed-text text
          :game/is-typing false}])
      ;; Посимвольное отображение
      (loop [idx 0]
        (when (< idx (count text))
          (js/setTimeout
            (fn []
              (let [current-text (subs text 0 (inc idx))]
                (d/transact! db/conn
                  [{:db/id [:game/current-line]
                    :game/displayed-text current-text}])
                (when (= idx (dec (count text)))
                  (d/transact! db/conn
                    [{:db/id [:game/current-line]
                      :game/is-typing false}]))
                (recur (inc idx))))
            (* idx speed)))))))

(defn skip-typing!
  "Пропускает анимацию печати и показывает весь текст"
  []
  (let [db @db/conn
        game-state (db/game-state db)
        scene (db/current-scene db)
        lines (db/scene-lines db (:db/id scene))
        current-line-idx (:game/current-line game-state)
        current-line (nth lines current-line-idx nil)]
    (when (and current-line (:game/is-typing game-state))
      (d/transact! db/conn
        [{:db/id [:game/current-line]
          :game/displayed-text (:line/text current-line)
          :game/is-typing false}]))))
```

### 3.2 Обработка кликов
```clojure
(defn handle-click!
  "Обработка клика по экрану"
  []
  (let [db @db/conn
        game-state (db/game-state db)]
    (if (:game/is-typing game-state)
      ;; Если текст печатается - пропускаем до конца
      (skip-typing!)
      ;; Иначе переходим к следующей строке
      (advance-line!))))

(defn advance-line!
  "Переход к следующей строке диалога"
  []
  (let [db @db/conn
        game-state (db/game-state db)
        scene (db/current-scene db)
        lines (db/scene-lines db (:db/id scene))
        current-line (:game/current-line game-state)
        next-line-idx (inc current-line)]
    (if (< next-line-idx (count lines))
      ;; Есть следующая строка
      (do
        (d/transact! db/conn
          [{:db/id [:game/current-line]
            :game/current-line next-line-idx}])
        (start-typing! (:line/text (nth lines next-line-idx))))
      ;; Сцена закончилась - переход к следующей
      (advance-scene!))))
```

## 4. Система переходов между сценами

### 4.1 Движок сценария
```clojure
;; src/scenarist/engine/script.cljs
(ns scenarist.engine.script
  (:require [datascript.core :as d]
            [scenarist.db :as db]))

(defmulti execute-command
  "Мультиметод для выполнения команд сценария"
  :type)

(defmethod execute-command :scene
  [{:keys [id name background music lines]}]
  ;; Создаем новую сцену в БД
  (let [line-ids (map-indexed 
                   (fn [idx line]
                     (let [line-id (keyword (str "line-" id "-" idx))]
                       (d/transact! db/conn
                         [{:db/id line-id
                           :line/id line-id
                           :line/text (:text line)
                           :line/speaker (:speaker line)
                           :line/position idx}])
                       line-id))
                   lines)]
    (d/transact! db/conn
      [{:db/id id
        :scene/id id
        :scene/name name
        :scene/background background
        :scene/music music
        :scene/lines line-ids}])))

(defmethod execute-command :jump
  [{:keys [target]}]
  ;; Переход к другой сцене
  (d/transact! db/conn
    [{:db/id [:game/current-line]
      :game/current-scene [:scene/id target]
      :game/current-line 0}])
  ;; Запускаем первую строку новой сцены
  (let [db @db/conn
        scene (db/current-scene db)
        lines (db/scene-lines db (:db/id scene))]
    (when (seq lines)
      (start-typing! (:line/text (first lines))))))

(defmethod execute-command :choice
  [{:keys [text options]}]
  ;; Отображение выбора (пока просто заглушка)
  (println "Choice:" text "Options:" options))
```

### 4.2 Функции перехода
```clojure
(defn advance-scene!
  "Переход к следующей сцене"
  []
  ;; В будущем здесь будет логика определения следующей сцены
  ;; Пока просто показываем сообщение о конце
  (d/transact! db/conn
    [{:db/id [:game/current-line]
      :game/displayed-text "Конец сцены. Продолжение следует..."}]))

(defn load-scene!
  "Загрузка сцены по ID"
  [scene-id]
  (d/transact! db/conn
    [{:db/id [:game/current-line]
      :game/current-scene [:scene/id scene-id]
      :game/current-line 0
      :game/displayed-text ""}])
  ;; Запускаем первую строку
  (let [db @db/conn
        scene (db/current-scene db)
        lines (db/scene-lines db (:db/id scene))]
    (when (seq lines)
      (start-typing! (:line/text (first lines))))))
```

## 5. Парсер сценария

### 5.1 Формат сценария (EDN)
```clojure
;; resources/public/scenarios/demo.edn
{:metadata {:title "Демо сценарий"
            :author "SCM Scenarist"
            :version "1.0"}
 
 :scenes
 [{:id :scene/intro
   :name "Пролог"
   :background "/images/backgrounds/school_morning.jpg"
   :music "/audio/bgm/peaceful.mp3"
   :lines
   [{:speaker nil
     :text "Раннее утро. Солнечные лучи пробиваются сквозь занавески..."}
    {:speaker "Главный герой"
     :text "Опять проспал! Нужно поторопиться в школу."}
    {:speaker "Главный герой"
     :text "Хотя... может, стоит прогулять первый урок?"}]}
  
  {:id :scene/school-gate
   :name "У школьных ворот"
   :background "/images/backgrounds/school_gate.jpg"
   :music "/audio/bgm/school_theme.mp3"
   :lines
   [{:speaker "Одноклассница"
     :text "О, ты всё-таки пришёл! Я думала, ты сегодня не появишься."}
    {:speaker "Главный герой"
     :text "Конечно пришёл. Не могу же я пропустить контрольную по математике."}
    {:speaker "Одноклассница"
     :text "Контрольная? Но она же была вчера!"}
    {:speaker "Главный герой"
     :text "..."}]}]}
```

### 5.2 Загрузчик сценария
```clojure
;; src/scenarist/engine/loader.cljs
(ns scenarist.engine.loader
  (:require [cljs.reader :as reader]
            [scenarist.engine.script :as script]))

(defn load-scenario!
  "Загружает сценарий из EDN файла"
  [url]
  (-> (js/fetch url)
      (.then #(.text %))
      (.then (fn [text]
               (let [scenario (reader/read-string text)]
                 (process-scenario! scenario))))))

(defn process-scenario!
  "Обрабатывает загруженный сценарий"
  [{:keys [metadata scenes]}]
  (println "Loading scenario:" (:title metadata))
  
  ;; Загружаем все сцены
  (doseq [scene scenes]
    (script/execute-command (assoc scene :type :scene)))
  
  ;; Переходим к первой сцене
  (when (seq scenes)
    (script/execute-command
      {:type :jump
       :target (:id (first scenes))})))
```

## 6. Интеграция и запуск

### 6.1 Обновленный core.cljs
```clojure
;; src/scenarist/core.cljs
(ns scenarist.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [scenarist.db :as db]
            [scenarist.ui.nvl :as nvl]
            [scenarist.engine.loader :as loader]))

(defn app []
  [:div.app
   {:on-click #(nvl/handle-click!)
    :style {:width "100vw"
            :height "100vh"
            :cursor "pointer"
            :user-select "none"}}
   [nvl/nvl-game-screen]])

(defn init []
  (println "Initializing SCM Scenarist...")
  ;; Инициализируем состояние игры
  (db/init-game-state!)
  ;; Загружаем демо-сценарий
  (loader/load-scenario! "/scenarios/demo.edn")
  ;; Рендерим приложение
  (rdom/render [app]
               (.getElementById js/document "app")))

(defn ^:dev/after-load reload []
  (init))
```

## Результат первой фазы

После завершения первой фазы у нас будет:

1. **Полноценный NVL движок** с красивым отображением текста поверх фонов
2. **Посимвольное появление текста** с настраиваемой скоростью
3. **Система сцен и переходов** между ними
4. **Загрузка сценариев** из внешних EDN файлов
5. **Базовое управление** (клик для продолжения, кнопки управления)

### Что можно будет делать:
- Создавать визуальные новеллы, написав сценарий в EDN формате
- Читать историю с красивым посимвольным появлением текста
- Видеть фоновые изображения для каждой сцены
- Управлять скоростью текста
- Переходить между сценами

### Демо-контент для тестирования:
Нужно будет добавить:
- 2-3 фоновых изображения (школа, улица, комната)
- Простой демо-сценарий на 5-10 минут чтения
- Базовые звуковые эффекты (опционально)

Это будет полностью рабочий прототип, на основе которого можно развивать более сложные функции в следующих фазах.