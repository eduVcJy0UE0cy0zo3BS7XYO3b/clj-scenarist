(ns scenarist.integration-test
  (:require [cljs.test :refer-macros [deftest testing is async use-fixtures]]
            [datascript.core :as d]
            [scenarist.db :as db]
            [scenarist.engine.script :as script]
            [scenarist.engine.typewriter :as typewriter]))

(defn setup-test-env []
  "Создаёт чистое окружение для интеграционных тестов"
  (let [conn (d/create-conn db/schema)]
    (with-redefs [db/conn conn]
      (db/init-game-state!)
      conn)))

(defn create-test-scenario []
  "Создаёт полный тестовый сценарий с несколькими сценами"
  (script/add-scene!
    {:id :intro
     :name "Введение"
     :background "/test/intro-bg.jpg"
     :music "/test/intro-music.mp3"
     :lines
     [{:text "Это начало нашей истории..."
       :speaker nil}
      {:text "Меня зовут Тест, и я главный герой."
       :speaker "Тест"}
      {:text "Сегодня я отправляюсь в приключение!"
       :speaker "Тест"}]})
  
  (script/add-scene!
    {:id :middle
     :name "Середина"
     :background "/test/middle-bg.jpg"
     :music "/test/middle-music.mp3"
     :lines
     [{:text "Путешествие оказалось долгим..."
       :speaker nil}
      {:text "Но я не сдаюсь!"
       :speaker "Тест"}]})
  
  (script/add-scene!
    {:id :ending
     :name "Финал"
     :background "/test/ending-bg.jpg"
     :music "/test/ending-music.mp3"
     :lines
     [{:text "И вот, наконец, я достиг цели."
       :speaker "Тест"}
      {:text "Конец."
       :speaker nil}]}))

(deftest test-full-game-scenario
  (testing "Полный сценарий игры от начала до конца"
    (let [conn (setup-test-env)]
      (with-redefs [db/conn conn]
        ;; Создаём тестовый сценарий
        (create-test-scenario)
        
        ;; Устанавливаем callback для переходов между сценами
        (typewriter/set-end-of-scene-callback! script/advance-scene!)
        
        ;; Начинаем с первой сцены
        (script/jump-to-scene! :intro)
        
        ;; Проверяем начальное состояние
        (let [db @conn
              current-scene (d/entity db (db/current-scene db))
              game-state (db/game-state db)]
          (is (= (:scene/id current-scene) :intro))
          (is (= (:game/current-line game-state) 0))
          (is (= (:game/displayed-text game-state) "")))))))

(deftest test-scene-transitions
  (testing "Переходы между сценами через клики"
    (let [conn (setup-test-env)]
      (with-redefs [db/conn conn]
        (create-test-scenario)
        (typewriter/set-end-of-scene-callback! script/advance-scene!)
        (script/jump-to-scene! :intro)
        
        ;; Устанавливаем мгновенную скорость для тестов
        (db/set-text-speed! :instant)
        
        ;; Проходим первую сцену
        (typewriter/handle-click!) ; Показать первую строку
        (typewriter/handle-click!) ; Перейти ко второй
        (typewriter/handle-click!) ; Перейти к третьей
        (typewriter/handle-click!) ; Закончить сцену и перейти к следующей
        
        ;; Проверяем, что мы во второй сцене
        (let [db @conn
              current-scene (d/entity db (db/current-scene db))]
          (is (= (:scene/id current-scene) :middle)))
        
        ;; Проходим вторую сцену
        (typewriter/handle-click!) ; Первая строка
        (typewriter/handle-click!) ; Вторая строка
        (typewriter/handle-click!) ; Переход к третьей сцене
        
        ;; Проверяем финальную сцену
        (let [db @conn
              current-scene (d/entity db (db/current-scene db))]
          (is (= (:scene/id current-scene) :ending)))))))

(deftest test-text-display-cycle
  (testing "Полный цикл отображения текста"
    (let [conn (setup-test-env)]
      (with-redefs [db/conn conn]
        (create-test-scenario)
        (script/jump-to-scene! :intro)
        
        ;; Тестируем с медленной скоростью
        (db/set-text-speed! :slow)
        
        ;; Запускаем первую строку
        (typewriter/start-scene! (:db/id (db/current-scene @conn)))
        
        ;; Проверяем, что печать началась
        (let [game-state (db/game-state @conn)]
          (is (= (:game/is-typing game-state) true)))
        
        ;; Пропускаем анимацию
        (typewriter/skip-typing!)
        
        ;; Проверяем результат
        (let [game-state (db/game-state @conn)
              lines (db/scene-lines @conn (:db/id (db/current-scene @conn)))]
          (is (= (:game/is-typing game-state) false))
          (is (= (:game/displayed-text game-state) 
                 (:line/text (first lines)))))))))

(deftest test-game-state-persistence
  (testing "Сохранение состояния игры между действиями"
    (let [conn (setup-test-env)]
      (with-redefs [db/conn conn]
        (create-test-scenario)
        (script/jump-to-scene! :intro)
        (db/set-text-speed! :fast)
        
        ;; Продвигаемся на несколько строк
        (db/set-text-speed! :instant)
        (typewriter/handle-click!)
        (typewriter/handle-click!)
        
        ;; Сохраняем текущее состояние
        (let [saved-state (db/game-state @conn)
              saved-scene-id (:scene/id (d/entity @conn (db/current-scene @conn)))]
          
          ;; Изменяем состояние
          (script/jump-to-scene! :ending)
          
          ;; Проверяем, что состояние изменилось
          (let [new-scene-id (:scene/id (d/entity @conn (db/current-scene @conn)))]
            (is (not= saved-scene-id new-scene-id)))
          
          ;; Возвращаемся к сохранённой сцене
          (script/jump-to-scene! saved-scene-id)
          
          ;; Состояние должно сброситься (строка 0)
          (let [restored-state (db/game-state @conn)]
            (is (= (:game/current-line restored-state) 0))))))))

(deftest test-multiple-speed-settings
  (testing "Работа с разными скоростями текста"
    (let [conn (setup-test-env)]
      (with-redefs [db/conn conn]
        (create-test-scenario)
        (script/jump-to-scene! :intro)
        
        ;; Тестируем все скорости
        (doseq [speed [:slow :medium :fast :instant]]
          (db/set-text-speed! speed)
          (let [game-state (db/game-state @conn)]
            (is (= (:game/text-speed game-state) speed))))))))

(deftest test-scene-content-integrity
  (testing "Целостность контента сцен"
    (let [conn (setup-test-env)]
      (with-redefs [db/conn conn]
        (create-test-scenario)
        
        ;; Проверяем каждую сцену
        (doseq [scene-id [:intro :middle :ending]]
          (script/jump-to-scene! scene-id)
          (let [db @conn
                current-scene (d/entity db (db/current-scene db))
                lines (db/scene-lines db (:db/id current-scene))]
            
            ;; Проверяем метаданные сцены
            (is (some? (:scene/name current-scene)))
            (is (some? (:scene/background current-scene)))
            (is (pos? (count lines)))
            
            ;; Проверяем строки
            (doseq [line lines]
              (is (string? (:line/text line)))
              (is (number? (:line/position line))))))))))

(deftest test-end-of-game-behavior
  (testing "Поведение при достижении конца игры"
    (let [conn (setup-test-env)]
      (with-redefs [db/conn conn
                    println (fn [& args] nil)] ; заглушаем вывод
        (create-test-scenario)
        (typewriter/set-end-of-scene-callback! script/advance-scene!)
        
        ;; Переходим к последней сцене
        (script/jump-to-scene! :ending)
        (db/set-text-speed! :instant)
        
        ;; Проходим все строки последней сцены
        (typewriter/handle-click!)
        (typewriter/handle-click!)
        (typewriter/handle-click!) ; Попытка перейти дальше конца
        
        ;; Проверяем, что игра завершилась корректно
        (let [game-state (db/game-state @conn)]
          (is (= (:game/displayed-text game-state) "Конец игры"))
          (is (= (:game/is-typing game-state) false)))))))

(deftest test-concurrent-operations
  (testing "Одновременные операции не ломают состояние"
    (let [conn (setup-test-env)]
      (with-redefs [db/conn conn]
        (create-test-scenario)
        (script/jump-to-scene! :intro)
        
        ;; Запускаем печать
        (db/set-text-speed! :medium)
        (typewriter/start-typing! "Тестовый текст для одновременных операций")
        
        ;; Пытаемся изменить состояние во время печати
        (db/set-text-speed! :fast)
        (typewriter/skip-typing!)
        
        ;; Состояние должно быть консистентным
        (let [game-state (db/game-state @conn)]
          (is (= (:game/text-speed game-state) :fast))
          (is (= (:game/is-typing game-state) false))
          (is (= (:game/displayed-text game-state) 
                 "Тестовый текст для одновременных операций")))))))

;; Функция для запуска всех интеграционных тестов
(defn run-tests []
  (cljs.test/run-tests))