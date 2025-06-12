(ns scenarist.e2e-test
  (:require [cljs.test :refer-macros [deftest testing is async]]
            [datascript.core :as d]
            [scenarist.db :as db]
            [scenarist.engine.script :as script]
            [scenarist.engine.typewriter :as typewriter]
            [scenarist.ui.nvl :as nvl]))

(defn create-e2e-scenario []
  "Создаёт полноценный сценарий для end-to-end тестирования"
  (let [conn (d/create-conn db/schema)]
    (with-redefs [db/conn conn]
      (db/init-game-state!)
      
      ;; Создаём сложный сценарий с ветвлениями
      (script/add-scene!
        {:id :start
         :name "Начало"
         :background "/e2e/start-bg.jpg"
         :music "/e2e/start-music.mp3"
         :lines
         [{:text "Добро пожаловать в мир приключений!"
           :speaker nil}
          {:text "Я твой проводник в этом путешествии."
           :speaker "Проводник"}
          {:text "Готов ли ты начать?"
           :speaker "Проводник"}]})
      
      (script/add-scene!
        {:id :path-a
         :name "Путь А"
         :background "/e2e/path-a-bg.jpg"
         :music "/e2e/path-a-music.mp3"
         :lines
         [{:text "Ты выбрал путь через лес."
           :speaker nil}
          {:text "Здесь темно и загадочно..."
           :speaker nil}
          {:text "Но я чувствую, что мы на правильном пути!"
           :speaker "Герой"}]})
      
      (script/add-scene!
        {:id :path-b
         :name "Путь Б"
         :background "/e2e/path-b-bg.jpg"
         :music "/e2e/path-b-music.mp3"
         :lines
         [{:text "Ты выбрал путь через горы."
           :speaker nil}
          {:text "Подъём крутой, но вид потрясающий!"
           :speaker "Герой"}]})
      
      (script/add-scene!
        {:id :convergence
         :name "Встреча путей"
         :background "/e2e/convergence-bg.jpg"
         :music "/e2e/convergence-music.mp3"
         :lines
         [{:text "Оба пути привели к одной точке."
           :speaker nil}
          {:text "Иногда разные дороги ведут к одной цели."
           :speaker "Мудрец"}
          {:text "Твоё путешествие подходит к концу..."
           :speaker "Мудрец"}]})
      
      (script/add-scene!
        {:id :ending
         :name "Финал"
         :background "/e2e/ending-bg.jpg"
         :music "/e2e/ending-music.mp3"
         :lines
         [{:text "И так заканчивается наша история."
           :speaker nil}
          {:text "Спасибо, что прошёл этот путь со мной!"
           :speaker "Проводник"}
          {:text "До новых встреч!"
           :speaker "Проводник"}]})
      
      conn)))

(deftest test-complete-playthrough
  (testing "Полное прохождение игры от начала до конца"
    (let [conn (create-e2e-scenario)]
      (with-redefs [db/conn conn]
        ;; Настраиваем автоматические переходы
        (typewriter/set-end-of-scene-callback! 
          (fn []
            (let [current-scene-id (:scene/id (d/entity @conn (db/current-scene @conn)))]
              (case current-scene-id
                :start (script/jump-to-scene! :path-a)
                :path-a (script/jump-to-scene! :convergence)
                :path-b (script/jump-to-scene! :convergence)
                :convergence (script/jump-to-scene! :ending)
                :ending (script/advance-scene!)))))
        
        ;; Начинаем игру
        (script/jump-to-scene! :start)
        (db/set-text-speed! :instant)
        
        ;; Проходим начальную сцену
        (dotimes [_ 3]
          (typewriter/handle-click!))
        (typewriter/handle-click!) ; Переход к path-a
        
        ;; Проверяем, что мы на пути А
        (is (= (:scene/id (d/entity @conn (db/current-scene @conn))) :path-a))
        
        ;; Проходим путь А
        (dotimes [_ 3]
          (typewriter/handle-click!))
        (typewriter/handle-click!) ; Переход к convergence
        
        ;; Проверяем схождение путей
        (is (= (:scene/id (d/entity @conn (db/current-scene @conn))) :convergence))
        
        ;; Доходим до финала
        (dotimes [_ 3]
          (typewriter/handle-click!))
        (typewriter/handle-click!) ; Переход к ending
        
        (is (= (:scene/id (d/entity @conn (db/current-scene @conn))) :ending))))))

(deftest test-alternative-path
  (testing "Альтернативный путь через игру"
    (let [conn (create-e2e-scenario)]
      (with-redefs [db/conn conn]
        ;; Настраиваем переход на путь B
        (typewriter/set-end-of-scene-callback! 
          (fn []
            (let [current-scene-id (:scene/id (d/entity @conn (db/current-scene @conn)))]
              (case current-scene-id
                :start (script/jump-to-scene! :path-b) ; Выбираем путь B
                :path-b (script/jump-to-scene! :convergence)
                :convergence (script/jump-to-scene! :ending)
                :ending (script/advance-scene!)))))
        
        (script/jump-to-scene! :start)
        (db/set-text-speed! :instant)
        
        ;; Проходим начало
        (dotimes [_ 4]
          (typewriter/handle-click!))
        
        ;; Проверяем, что мы на пути B
        (is (= (:scene/id (d/entity @conn (db/current-scene @conn))) :path-b))))))

(deftest test-scene-metadata-consistency
  (testing "Консистентность метаданных сцен при переходах"
    (let [conn (create-e2e-scenario)]
      (with-redefs [db/conn conn]
        (db/set-text-speed! :instant)
        
        ;; Проходим по всем сценам и проверяем метаданные
        (doseq [scene-id [:start :path-a :path-b :convergence :ending]]
          (script/jump-to-scene! scene-id)
          (let [scene (d/entity @conn (db/current-scene @conn))]
            (is (= (:scene/id scene) scene-id))
            (is (string? (:scene/name scene)))
            (is (string? (:scene/background scene)))
            (is (string? (:scene/music scene)))
            (is (pos? (count (db/scene-lines @conn (:db/id scene)))))))))))

(deftest test-speed-changes-during-playthrough
  (testing "Изменение скорости во время прохождения"
    (let [conn (create-e2e-scenario)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :start)
        
        ;; Начинаем с медленной скорости
        (db/set-text-speed! :slow)
        (typewriter/start-scene! (:db/id (db/current-scene @conn)))
        (is (= (:game/text-speed (db/game-state @conn)) :slow))
        
        ;; Меняем на быструю во время печати
        (db/set-text-speed! :fast)
        (is (= (:game/text-speed (db/game-state @conn)) :fast))
        
        ;; Пропускаем до конца
        (typewriter/skip-typing!)
        
        ;; Переходим к следующей строке с мгновенной скоростью
        (db/set-text-speed! :instant)
        (typewriter/handle-click!)
        
        ;; Проверяем, что текст появился мгновенно
        (is (= (:game/is-typing (db/game-state @conn)) false))))))

(deftest test-interruption-and-resume
  (testing "Прерывание и возобновление игры"
    (let [conn (create-e2e-scenario)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :start)
        (db/set-text-speed! :instant)
        
        ;; Продвигаемся на две строки
        (typewriter/handle-click!)
        (typewriter/handle-click!)
        
        ;; Сохраняем состояние
        (let [saved-line (:game/current-line (db/game-state @conn))
              saved-scene-id (:scene/id (d/entity @conn (db/current-scene @conn)))]
          
          ;; "Прерываем" игру - переходим в другое место
          (script/jump-to-scene! :ending)
          
          ;; "Возобновляем" - возвращаемся
          (script/jump-to-scene! saved-scene-id)
          
          ;; Строка должна сброситься на 0 при переходе
          (is (= (:game/current-line (db/game-state @conn)) 0))
          (is (= (:scene/id (d/entity @conn (db/current-scene @conn))) saved-scene-id)))))))

(deftest test-rapid-clicking
  (testing "Быстрые клики не ломают состояние"
    (let [conn (create-e2e-scenario)
          errors (atom [])]
      (with-redefs [db/conn conn
                    println (fn [& args] 
                              (when (some #(re-find #"error|Error" (str %)) args)
                                (swap! errors conj args)))]
        (script/jump-to-scene! :start)
        (db/set-text-speed! :medium)
        
        ;; Симулируем очень быстрые клики
        (dotimes [_ 20]
          (typewriter/handle-click!)
          ;; Небольшая задержка для имитации реальных кликов
          (js/setTimeout identity 10))
        
        ;; Не должно быть ошибок
        (is (empty? @errors))
        
        ;; Состояние должно быть консистентным
        (let [state (db/game-state @conn)]
          (is (number? (:game/current-line state)))
          (is (keyword? (:game/text-speed state)))
          (is (boolean? (:game/is-typing state))))))))

(deftest test-full-game-with-all-features
  (testing "Полная игра со всеми функциями"
    (let [conn (create-e2e-scenario)
          scenes-visited (atom [])]
      (with-redefs [db/conn conn]
        ;; Настраиваем отслеживание посещённых сцен
        (typewriter/set-end-of-scene-callback! 
          (fn []
            (let [current-scene-id (:scene/id (d/entity @conn (db/current-scene @conn)))]
              (swap! scenes-visited conj current-scene-id)
              (case current-scene-id
                :start (script/jump-to-scene! :path-a)
                :path-a (script/jump-to-scene! :convergence)
                :convergence (script/jump-to-scene! :ending)
                :ending (script/advance-scene!)))))
        
        ;; Проходим всю игру с разными скоростями
        (script/jump-to-scene! :start)
        
        ;; Начальная сцена - медленно
        (db/set-text-speed! :slow)
        (typewriter/handle-click!)
        (typewriter/skip-typing!) ; Пропускаем медленную анимацию
        
        ;; Вторая строка - средне
        (db/set-text-speed! :medium)
        (typewriter/handle-click!)
        
        ;; Третья строка - быстро
        (db/set-text-speed! :fast)
        (typewriter/handle-click!)
        
        ;; Переход к следующей сцене - мгновенно
        (db/set-text-speed! :instant)
        (typewriter/handle-click!)
        
        ;; Проходим остальные сцены
        (while (not= (:scene/id (d/entity @conn (db/current-scene @conn))) :ending)
          (let [lines (db/scene-lines @conn (:db/id (db/current-scene @conn)))]
            (dotimes [_ (count lines)]
              (typewriter/handle-click!))
            (typewriter/handle-click!))) ; Переход к следующей сцене
        
        ;; Проверяем, что прошли все ожидаемые сцены
        (is (= @scenes-visited [:start :path-a :convergence]))))))

;; Функция для запуска всех e2e тестов
(defn run-tests []
  (cljs.test/run-tests))