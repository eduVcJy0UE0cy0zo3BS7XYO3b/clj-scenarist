(ns scenarist.engine.typewriter
  (:require [datascript.core :as d]
            [scenarist.db :as db]))

(def speed-map
  {:slow 100
   :medium 50
   :fast 20
   :instant 0})

(defonce typing-timeout (atom nil))

(defn clear-typing! []
  "Остановить текущую анимацию печати"
  (when @typing-timeout
    (js/clearTimeout @typing-timeout)
    (reset! typing-timeout nil)))

(defn start-typing!
  "Начинает посимвольный вывод текста"
  [text]
  (clear-typing!)
  (let [speed (-> @db/conn
                  db/game-state
                  :game/text-speed
                  speed-map)]
    (db/update-displayed-text! "")
    (db/set-typing-state! true)
    
    (if (= speed 0)
      ;; Мгновенное отображение
      (do
        (db/update-displayed-text! text)
        (db/set-typing-state! false))
      ;; Посимвольное отображение
      (letfn [(type-next-char [idx]
                (when (< idx (count text))
                  (let [current-text (subs text 0 (inc idx))]
                    (db/update-displayed-text! current-text)
                    (if (= idx (dec (count text)))
                      ;; Последний символ
                      (db/set-typing-state! false)
                      ;; Продолжаем печатать
                      (reset! typing-timeout
                        (js/setTimeout #(type-next-char (inc idx)) speed))))))]
        (type-next-char 0)))))

(defn skip-typing!
  "Пропускает анимацию печати и показывает весь текст"
  []
  (let [db @db/conn
        game-state (db/game-state db)
        scene-id (db/current-scene db)
        lines (when scene-id (db/scene-lines db scene-id))
        current-line-idx (:game/current-line game-state)
        current-line (nth lines current-line-idx nil)]
    (when (and current-line (:game/is-typing game-state))
      (clear-typing!)
      (db/update-displayed-text! (:line/text current-line))
      (db/set-typing-state! false))))

(defonce end-of-scene-callback (atom nil))

(defn set-end-of-scene-callback! [callback]
  "Устанавливает callback для конца сцены"
  (reset! end-of-scene-callback callback))

(defn advance-line!
  "Переход к следующей строке диалога"
  []
  (let [db @db/conn
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
  (let [db @db/conn
        game-state (db/game-state db)]
    (if (:game/is-typing game-state)
      ;; Если текст печатается - пропускаем до конца
      (skip-typing!)
      ;; Иначе переходим к следующей строке
      (advance-line!))))

(defn start-scene!
  "Запуск первой строки сцены"
  [scene-id]
  (let [db @db/conn
        lines (db/scene-lines db scene-id)]
    (when (seq lines)
      (start-typing! (:line/text (first lines))))))