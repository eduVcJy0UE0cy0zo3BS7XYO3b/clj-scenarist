(ns scenarist.db
  (:require [datascript.core :as d]
            [reagent.core :as r]))

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
   :game/id {:db/unique :db.unique/identity}
   :game/current-scene {:db/type :db.type/ref}
   :game/current-line {:db/type :db.type/long}
   :game/text-speed {:db/type :db.type/keyword}
   :game/displayed-text {:db/type :db.type/string}
   :game/is-typing {:db/type :db.type/boolean}})

(defonce conn (d/create-conn schema))

;; Реактивный атом для отслеживания изменений БД
(defonce db-state (r/atom @conn))

;; Слушатель изменений БД
(d/listen! conn :db-state
  (fn [tx-report]
    (reset! db-state (:db-after tx-report))))

;; Инициализация начального состояния
(defn init-game-state! []
  (d/transact! conn
    [{:game/id :main-game
      :game/current-line 0
      :game/text-speed :medium
      :game/displayed-text ""
      :game/is-typing false}]))

;; Получить текущую сцену
(defn current-scene [db]
  (d/q '[:find ?scene .
         :where [_ :game/current-scene ?scene]]
       db))

;; Реактивная версия получения текущей сцены
(defn current-scene-rx []
  (current-scene @db-state))

;; Реактивный pull текущей сцены с полными данными
(defn current-scene-data-rx []
  (let [scene-id (current-scene-rx)]
    (when scene-id
      (d/pull @db-state
              [:scene/id :scene/name :scene/background :scene/music :scene/lines]
              scene-id))))

;; Получить все строки текущей сцены
(defn scene-lines [db scene-id]
  (d/q '[:find [(pull ?line [:line/text :line/speaker :line/position]) ...]
         :in $ ?scene
         :where
         [?scene :scene/lines ?line]]
       db scene-id))

;; Реактивная версия получения строк сцены
(defn scene-lines-rx [scene-id]
  (scene-lines @db-state scene-id))

;; Получить текущее состояние игры
(defn game-state [db]
  (d/q '[:find (pull ?e [:game/current-line
                         :game/text-speed
                         :game/displayed-text
                         :game/is-typing]) .
         :where [?e :game/id :main-game]]
       db))

;; Реактивная версия состояния игры
(defn game-state-rx []
  (d/pull @db-state
          [:game/current-line
           :game/text-speed
           :game/displayed-text
           :game/is-typing
           {:game/current-scene [:scene/id]}]
          [:game/id :main-game]))

;; Реактивная версия получения текущей строки
(defn current-line-rx []
  (let [state (game-state-rx)
        scene-id (get-in state [:game/current-scene :scene/id])
        current-line-idx (:game/current-line state)]
    (when scene-id
      (let [lines (scene-lines-rx [:scene/id scene-id])]
        (nth lines current-line-idx nil)))))

;; Обновить текущую сцену
(defn set-current-scene! [scene-id]
  (d/transact! conn
    [{:db/id [:game/id :main-game]
      :game/current-scene [:scene/id scene-id]
      :game/current-line 0
      :game/displayed-text ""
      :game/is-typing false}]))

;; Обновить отображаемый текст
(defn update-displayed-text! [text]
  (d/transact! conn
    [{:db/id [:game/id :main-game]
      :game/displayed-text text}]))

;; Установить состояние печати
(defn set-typing-state! [is-typing]
  (d/transact! conn
    [{:db/id [:game/id :main-game]
      :game/is-typing is-typing}]))

;; Перейти к следующей строке
(defn advance-line! []
  (let [db @conn
        current-line (-> db game-state :game/current-line)]
    (d/transact! conn
      [{:db/id [:game/id :main-game]
        :game/current-line (inc current-line)
        :game/displayed-text ""
        :game/is-typing false}])))

;; Изменить скорость текста
(defn set-text-speed! [speed]
  (d/transact! conn
    [{:db/id [:game/id :main-game]
      :game/text-speed speed}]))