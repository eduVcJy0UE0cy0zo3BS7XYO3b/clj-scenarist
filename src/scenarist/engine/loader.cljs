(ns scenarist.engine.loader
  (:require [cljs.reader :as reader]
            [scenarist.engine.script :as script]))

(defn validate-scenario
  "Валидирует структуру сценария"
  [{:keys [metadata scenes]}]
  (and
    ;; Проверка метаданных
    (map? metadata)
    (string? (:title metadata))
    
    ;; Проверка сцен
    (sequential? scenes)
    (every? (fn [scene]
              (and (map? scene)
                   (keyword? (:id scene))
                   (string? (:name scene))
                   (sequential? (:lines scene))
                   (every? (fn [line]
                             (and (map? line)
                                  (string? (:text line))))
                           (:lines scene))))
            scenes)))

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

(defn load-scenario-from-string
  "Загружает сценарий из строки EDN"
  [edn-string]
  (try
    (let [scenario (reader/read-string edn-string)]
      (if (validate-scenario scenario)
        {:success true
         :scenario scenario}
        {:success false
         :error "Invalid scenario structure"}))
    (catch :default e
      {:success false
       :error (str "Parse error: " (.-message e))})))

(defn load-scenario!
  "Загружает сценарий из EDN файла"
  [url]
  (-> (js/fetch url)
      (.then #(.text %))
      (.then (fn [text]
               (let [result (load-scenario-from-string text)]
                 (if (:success result)
                   (process-scenario! (:scenario result))
                   (println "Error loading scenario:" (:error result))))))
      (.catch (fn [error]
                (println "Fetch error:" error)))))