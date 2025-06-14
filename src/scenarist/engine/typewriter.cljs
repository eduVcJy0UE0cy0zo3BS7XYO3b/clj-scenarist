(ns scenarist.engine.typewriter
  (:require [datascript.core :as d]
            [scenarist.db :as db]))

(def chars-per-second
  {:slow 10        ; 10 символов в секунду (медленнее)
   :medium 20      ; 20 символов в секунду (медленнее)
   :fast 40        ; 40 символов в секунду (медленнее)
   :instant nil})  ; мгновенно

(defn calculate-duration [text speed]
  "Вычисляет длительность анимации в миллисекундах"
  (let [cps (chars-per-second speed)]
    (if cps
      (* (count text) (/ 1000 cps))
      0)))

(defonce animation-timeout (atom nil))

(defn clear-animation! []
  "Остановить текущую анимацию"
  (when @animation-timeout
    (js/clearTimeout @animation-timeout)
    (reset! animation-timeout nil)))

(defn start-typing!
  "Запускает анимацию появления текста"
  [text]
  (clear-animation!)
  (let [speed (-> (d/db db/conn)
                  db/game-state
                  :game/text-speed)
        duration (calculate-duration text speed)]
    ;; Устанавливаем полный текст и начинаем анимацию
    (db/update-displayed-text! text)
    (db/set-typing-state! true)

    (if (= duration 0)
      ;; Мгновенное отображение
      (db/set-typing-state! false)
      ;; Запускаем таймер для окончания анимации
      (reset! animation-timeout
        (js/setTimeout
          #(db/set-typing-state! false)
          duration)))))

(defn skip-typing!
  "Пропускает анимацию и показывает весь текст"
  []
  (clear-animation!)
  (db/set-typing-state! false))

(defonce end-of-scene-callback (atom nil))

(defn set-end-of-scene-callback! [callback]
  "Устанавливает callback для конца сцены"
  (reset! end-of-scene-callback callback))

(defn advance-line!
  "Переход к следующей строке диалога"
  []
  (let [db (d/db db/conn)
        game-state (db/game-state db)
        scene-id (db/current-scene db)
        lines (when scene-id (db/scene-lines db scene-id))
        current-line (:game/current-line game-state)
        next-line-idx (inc current-line)]
    (if (and lines (< next-line-idx (count lines)))
      ;; Есть следующая строка
      (do
        (db/advance-line!)
        (start-typing! (:line/text (nth lines next-line-idx))))
      ;; Сцена закончилась
      (if @end-of-scene-callback
        (@end-of-scene-callback)
        (println "End of scene")))))

(defn handle-click!
  "Обработка клика по экрану"
  []
  (let [db (d/db db/conn)
        game-state (db/game-state db)]
    (if (:game/is-typing game-state)
      ;; Если текст анимируется - пропускаем анимацию
      (skip-typing!)
      ;; Иначе переходим к следующей строке
      (advance-line!))))

(defn start-scene!
  "Запуск первой строки сцены"
  [scene-id]
  (let [db (d/db db/conn)
        lines (db/scene-lines db scene-id)]
    (when (seq lines)
      (start-typing! (:line/text (first lines))))))
