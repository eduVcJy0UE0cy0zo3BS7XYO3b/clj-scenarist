(ns scenarist.engine.script
  (:require [datascript.core :as d]
            [posh.reagent :as p]
            [scenarist.db :as db]
            [scenarist.engine.typewriter :as typewriter]))

(defmulti execute-command
  "Мультиметод для выполнения команд сценария"
  :type)

(defmethod execute-command :scene
  [{:keys [id name background music lines]}]
  ;; Создаем строки диалога
  (let [id-str (cljs.core/name id)
        line-entities (map-indexed 
                        (fn [idx line]
                          (let [base-entity {:db/id (str "line-" id-str "-" idx)
                                             :line/id (keyword (str "line-" id-str "-" idx))
                                             :line/text (:text line)
                                             :line/position idx}]
                            (if (:speaker line)
                              (assoc base-entity :line/speaker (:speaker line))
                              base-entity)))
                        lines)
        line-ids (map :db/id line-entities)]
    
    ;; Добавляем строки в БД
    (p/transact! db/conn line-entities)
    
    ;; Создаем сцену
    (let [scene-entity (cond-> {:db/id (str "scene-" id-str)
                                 :scene/id id
                                 :scene/name name
                                 :scene/lines (map #(vector :line/id (keyword %)) line-ids)}
                         background (assoc :scene/background background)
                         music (assoc :scene/music music))]
      (p/transact! db/conn [scene-entity]))))

(defmethod execute-command :jump
  [{:keys [target]}]
  ;; Переход к другой сцене
  (db/set-current-scene! target)
  
  ;; Запускаем первую строку новой сцены
  (let [db (d/db db/conn)
        scene-id (db/current-scene db)]
    (when scene-id
      (typewriter/start-scene! scene-id))))

(defmethod execute-command :choice
  [{:keys [text options]}]
  ;; Отображение выбора (пока просто заглушка)
  (println "Choice:" text "Options:" options))

(defmethod execute-command :set-background
  [{:keys [image]}]
  ;; Смена фона текущей сцены
  (let [db (d/db db/conn)
        scene-id (db/current-scene db)]
    (when scene-id
      (p/transact! db/conn
        [{:db/id scene-id
          :scene/background image}]))))

(defmethod execute-command :play-music
  [{:keys [file loop]}]
  ;; Воспроизведение музыки (заглушка)
  (println "Playing music:" file "Loop:" loop))

(defmethod execute-command :default
  [command]
  (println "Unknown command:" (:type command)))

(defn add-scene!
  "Добавляет новую сцену в игру"
  [scene-data]
  (execute-command (assoc scene-data :type :scene)))

(defn jump-to-scene!
  "Переход к указанной сцене"
  [scene-id]
  (execute-command {:type :jump :target scene-id}))

(defn get-scene-count
  "Возвращает количество сцен в игре"
  []
  (let [db (d/db db/conn)]
    (count (d/q '[:find ?scene
                  :where [?scene :scene/id]]
                db))))

(defn get-all-scenes
  "Возвращает список всех сцен"
  []
  (let [db (d/db db/conn)]
    (d/q '[:find [(pull ?scene [:scene/id :scene/name]) ...]
           :where [?scene :scene/id]]
         db)))

(defn scene-exists?
  "Проверяет существование сцены"
  [scene-id]
  (let [db (d/db db/conn)]
    (some? (d/q '[:find ?scene .
                  :in $ ?id
                  :where [?scene :scene/id ?id]]
                db scene-id))))

(defn advance-scene!
  "Переход к следующей сцене в порядке добавления"
  []
  (let [db (d/db db/conn)
        current-scene-id (db/current-scene db)
        all-scenes (sort-by :scene/id (get-all-scenes))
        current-idx (.indexOf (map :scene/id all-scenes) 
                              (when current-scene-id
                                (:scene/id (d/entity db current-scene-id))))
        next-idx (inc current-idx)]
    
    (if (< next-idx (count all-scenes))
      ;; Есть следующая сцена
      (jump-to-scene! (:scene/id (nth all-scenes next-idx)))
      ;; Конец игры
      (do
        (println "End of game")
        (db/update-displayed-text! "Конец игры")
        (db/set-typing-state! false)))))